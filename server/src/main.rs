use jsonwebtoken::{Algorithm, EncodingKey, Header};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tiny_http::{Method, Response, Server, StatusCode};

const FCM_SCOPE: &str = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL: &str = "https://oauth2.googleapis.com/token";

#[derive(Deserialize, Clone)]
struct ServiceAccount {
    client_email: String,
    private_key: String,
    #[serde(default)]
    token_uri: Option<String>,
}

#[derive(Serialize)]
struct JwtClaims {
    iss: String,
    scope: String,
    aud: String,
    iat: i64,
    exp: i64,
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    expires_in: i64,
}

struct CachedToken {
    token: String,
    expires_at: i64,
}

struct AppState {
    service_account: ServiceAccount,
    project_id: String,
    shared_secret: String,
    cached_token: Mutex<Option<CachedToken>>,
}

/// Mints a fresh Google OAuth2 access token via the service-account JWT-bearer
/// flow (RFC 7523), caching it in memory until shortly before it expires.
fn get_access_token(state: &AppState) -> anyhow::Result<String> {
    {
        let cache = state.cached_token.lock().unwrap();
        if let Some(cached) = cache.as_ref() {
            let now = chrono::Utc::now().timestamp();
            if cached.expires_at - now > 60 {
                return Ok(cached.token.clone());
            }
        }
    }

    let now = chrono::Utc::now().timestamp();
    let claims = JwtClaims {
        iss: state.service_account.client_email.clone(),
        scope: FCM_SCOPE.to_string(),
        aud: state
            .service_account
            .token_uri
            .clone()
            .unwrap_or_else(|| TOKEN_URL.to_string()),
        iat: now,
        exp: now + 3600,
    };

    let key = EncodingKey::from_rsa_pem(state.service_account.private_key.as_bytes())?;
    let jwt = jsonwebtoken::encode(&Header::new(Algorithm::RS256), &claims, &key)?;

    let form_body = format!(
        "grant_type={}&assertion={}",
        urlencode("urn:ietf:params:oauth:grant-type:jwt-bearer"),
        urlencode(&jwt)
    );

    let resp: TokenResponse = ureq::post(TOKEN_URL)
        .set("Content-Type", "application/x-www-form-urlencoded")
        .send_string(&form_body)?
        .into_json()?;

    let expires_at = now + resp.expires_in;
    {
        let mut cache = state.cached_token.lock().unwrap();
        *cache = Some(CachedToken {
            token: resp.access_token.clone(),
            expires_at,
        });
    }

    Ok(resp.access_token)
}

/// Minimal application/x-www-form-urlencoded percent-encoding — good enough
/// for the fixed grant_type string and a base64url JWT (which never contains
/// bytes outside [A-Za-z0-9._-]).
fn urlencode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{:02X}", b)),
        }
    }
    out
}

#[derive(Deserialize)]
struct NotifyRequest {
    tokens: Vec<String>,
    title: String,
    body: String,
    #[serde(default, rename = "channelId")]
    channel_id: Option<String>,
    #[serde(default)]
    data: HashMap<String, String>,
}

#[derive(Serialize)]
struct TokenResult {
    token: String,
    success: bool,
    error: Option<String>,
}

#[derive(Serialize)]
struct NotifyResponse {
    results: Vec<TokenResult>,
}

fn send_fcm(state: &AppState, access_token: &str, token: &str, req: &NotifyRequest) -> TokenResult {
    let url = format!(
        "https://fcm.googleapis.com/v1/projects/{}/messages:send",
        state.project_id
    );
    let android_block = match &req.channel_id {
        Some(cid) => serde_json::json!({
            "priority": "high",
            "notification": { "channel_id": cid }
        }),
        None => serde_json::json!({ "priority": "high" }),
    };

    let message = serde_json::json!({
        "message": {
            "token": token,
            "notification": {
                "title": req.title,
                "body": req.body,
            },
            "data": req.data,
            "android": android_block
        }
    });

    match ureq::post(&url)
        .set("Authorization", &format!("Bearer {access_token}"))
        .send_json(message)
    {
        Ok(_) => TokenResult {
            token: token.to_string(),
            success: true,
            error: None,
        },
        Err(ureq::Error::Status(code, resp)) => {
            let text = resp.into_string().unwrap_or_default();
            log::warn!("FCM send failed ({code}): {text}");
            TokenResult {
                token: token.to_string(),
                success: false,
                error: Some(format!("{code}: {text}")),
            }
        }
        Err(e) => TokenResult {
            token: token.to_string(),
            success: false,
            error: Some(e.to_string()),
        },
    }
}

fn handle_notify(state: &AppState, mut request: tiny_http::Request) {
    let auth_ok = request
        .headers()
        .iter()
        .find(|h| h.field.as_str().as_str().eq_ignore_ascii_case("Authorization"))
        .map(|h| h.value.as_str() == format!("Bearer {}", state.shared_secret))
        .unwrap_or(false);

    if !auth_ok {
        let _ = request.respond(json_response(
            StatusCode(401),
            &serde_json::json!({"error": "unauthorized"}),
        ));
        return;
    }

    let mut body = String::new();
    if request.as_reader().read_to_string(&mut body).is_err() {
        let _ = request.respond(json_response(
            StatusCode(400),
            &serde_json::json!({"error": "failed to read body"}),
        ));
        return;
    }

    let payload: NotifyRequest = match serde_json::from_str(&body) {
        Ok(p) => p,
        Err(e) => {
            let _ = request.respond(json_response(
                StatusCode(400),
                &serde_json::json!({"error": format!("invalid json: {e}")}),
            ));
            return;
        }
    };

    if payload.tokens.is_empty() {
        let _ = request.respond(json_response(
            StatusCode(400),
            &serde_json::json!({"error": "tokens required"}),
        ));
        return;
    }

    let access_token = match get_access_token(state) {
        Ok(t) => t,
        Err(e) => {
            log::error!("oauth token error: {e:#}");
            let _ = request.respond(json_response(
                StatusCode(500),
                &serde_json::json!({"error": "auth failed"}),
            ));
            return;
        }
    };

    let results: Vec<TokenResult> = payload
        .tokens
        .iter()
        .map(|token| send_fcm(state, &access_token, token, &payload))
        .collect();

    let _ = request.respond(json_response(StatusCode(200), &NotifyResponse { results }));
}

fn json_response<T: Serialize>(status: StatusCode, body: &T) -> Response<std::io::Cursor<Vec<u8>>> {
    let bytes = serde_json::to_vec(body).unwrap_or_default();
    Response::from_data(bytes)
        .with_status_code(status)
        .with_header(
            tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap(),
        )
}

fn main() {
    dotenvy::dotenv().ok();
    env_logger::init_from_env(env_logger::Env::new().default_filter_or("info"));

    let port: u16 = std::env::var("PORT")
        .unwrap_or_else(|_| "8080".into())
        .parse()
        .expect("PORT must be a number");
    let shared_secret = std::env::var("SHARED_SECRET").expect("SHARED_SECRET must be set");
    let project_id = std::env::var("FCM_PROJECT_ID").expect("FCM_PROJECT_ID must be set");
    let sa_path = std::env::var("GOOGLE_SERVICE_ACCOUNT_JSON")
        .expect("GOOGLE_SERVICE_ACCOUNT_JSON must be set");

    let sa_json = std::fs::read_to_string(&sa_path)
        .unwrap_or_else(|e| panic!("failed to read {sa_path}: {e}"));
    let service_account: ServiceAccount =
        serde_json::from_str(&sa_json).expect("failed to parse service account json");

    let state = Arc::new(AppState {
        service_account,
        project_id,
        shared_secret,
        cached_token: Mutex::new(None),
    });

    let server = Server::http(("0.0.0.0", port)).expect("failed to bind server");
    log::info!("listening on 0.0.0.0:{port}");

    // A small fixed pool of worker threads pulling requests off the shared
    // listener -- plenty for a low-traffic internal webhook, no async runtime
    // needed.
    let server = Arc::new(server);
    let mut handles = Vec::new();
    for _ in 0..4 {
        let server = Arc::clone(&server);
        let state = Arc::clone(&state);
        handles.push(std::thread::spawn(move || loop {
            match server.recv() {
                Ok(request) => {
                    let method = request.method().clone();
                    let url = request.url().to_string();
                    match (method, url.as_str()) {
                        (Method::Get, "/health") => {
                            let _ = request.respond(json_response(
                                StatusCode(200),
                                &serde_json::json!({"status": "ok"}),
                            ));
                        }
                        (Method::Post, "/notify") => handle_notify(&state, request),
                        _ => {
                            let _ = request.respond(json_response(
                                StatusCode(404),
                                &serde_json::json!({"error": "not found"}),
                            ));
                        }
                    }
                }
                Err(e) => {
                    log::error!("server.recv error: {e}");
                    break;
                }
            }
        }));
    }
    for h in handles {
        let _ = h.join();
    }
}
