#![windows_subsystem = "windows"]
use jsonwebtoken::{Algorithm, EncodingKey, Header};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::ffi::OsString;
use std::sync::{Arc, Mutex};
use tiny_http::{Method, Response, Server, StatusCode};
use windows_service::{
    service::{
        ServiceControl, ServiceControlAccept, ServiceExitCode, ServiceState, ServiceStatus,
        ServiceType,
    },
    service_control_handler::{self, ServiceControlHandlerResult},
    service_dispatcher,
};

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
    log::info!("notify request received from {:?}", request.remote_addr());

    let auth_ok = request
        .headers()
        .iter()
        .find(|h| {
            h.field
                .as_str()
                .as_str()
                .eq_ignore_ascii_case("Authorization")
        })
        .map(|h| h.value.as_str() == format!("Bearer {}", state.shared_secret))
        .unwrap_or(false);

    if !auth_ok {
        log::warn!("notify request rejected: missing/invalid Authorization header");
        let _ = request.respond(json_response(
            StatusCode(401),
            &serde_json::json!({"error": "unauthorized"}),
        ));
        return;
    }

    let mut body = String::new();
    if request.as_reader().read_to_string(&mut body).is_err() {
        log::warn!("notify request rejected: failed to read body");
        let _ = request.respond(json_response(
            StatusCode(400),
            &serde_json::json!({"error": "failed to read body"}),
        ));
        return;
    }

    let payload: NotifyRequest = match serde_json::from_str(&body) {
        Ok(p) => p,
        Err(e) => {
            log::warn!("notify request rejected: invalid json: {e}");
            let _ = request.respond(json_response(
                StatusCode(400),
                &serde_json::json!({"error": format!("invalid json: {e}")}),
            ));
            return;
        }
    };

    if payload.tokens.is_empty() {
        log::warn!("notify request rejected: no tokens in payload");
        let _ = request.respond(json_response(
            StatusCode(400),
            &serde_json::json!({"error": "tokens required"}),
        ));
        return;
    }

    log::info!(
        "notify: {} token(s), title={:?}, channel={:?}",
        payload.tokens.len(),
        payload.title,
        payload.channel_id
    );

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

    let ok_count = results.iter().filter(|r| r.success).count();
    log::info!("notify: {}/{} sends succeeded", ok_count, results.len());
    for r in &results {
        if !r.success {
            log::warn!(
                "notify: token ...{} failed: {:?}",
                &r.token[r.token.len().saturating_sub(8)..],
                r.error
            );
        }
    }

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

/// Sets up flexi_logger writing to a rotating log file (`logs/` next to the
/// exe). Kept as its own fallible function since `my_service_main`'s
/// signature is fixed by `define_windows_service!` and can't return `Result`,
/// so it can't use `?` directly -- this lets the `?`s live here instead.
fn init_logger() -> Result<(), Box<dyn std::error::Error>> {
    flexi_logger::Logger::try_with_env_or_str("info")?
        .log_to_file(flexi_logger::FileSpec::default().directory("logs"))
        .rotate(
            flexi_logger::Criterion::Size(5_000_000),
            flexi_logger::Naming::Timestamps,
            flexi_logger::Cleanup::KeepLogFiles(5),
        )
        .start()?;
    Ok(())
}

// 1. Define the service entry point macro
windows_service::define_windows_service!(ffi_service_main, my_service_main);

// 2. The ACTUAL service entry point called by Windows
fn my_service_main(_arguments: Vec<OsString>) {
    // First, register the control handler
    let event_handler = move |control_event| -> ServiceControlHandlerResult {
        match control_event {
            ServiceControl::Stop | ServiceControl::Shutdown => {
                log::info!("Received stop signal, shutting down...");
                std::process::exit(0);
            }
            _ => ServiceControlHandlerResult::NoError,
        }
    };

    let status_handle = service_control_handler::register("MyServer", event_handler)
        .expect("Failed to register service control handler");

    // 1. Change to the correct directory
    let exe_path = std::env::current_exe().expect("Failed to get executable path");
    let exe_dir = exe_path
        .parent()
        .expect("Failed to get executable directory");
    std::env::set_current_dir(exe_dir).expect("Failed to set working directory");

    // 2. Load .env
    dotenvy::dotenv().ok();
    init_logger().expect("failed to initialize logger");

    // 3. Read configuration
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

    // 4. Create the server
    let server = Server::http(("0.0.0.0", port)).expect("failed to bind server");
    log::info!("listening on 0.0.0.0:{port}");

    // 5. Tell Windows the service is now RUNNING
    let status = ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: ServiceState::Running,
        controls_accepted: ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
        exit_code: ServiceExitCode::Win32(0),
        checkpoint: 0,
        wait_hint: std::time::Duration::from_secs(0),
        process_id: Some(std::process::id()),
    };

    if let Err(e) = status_handle.set_service_status(status) {
        log::error!("Failed to set service status: {}", e);
        return;
    }

    // 6. Start worker threads
    let server = Arc::new(server);
    for _ in 0..4 {
        let server = Arc::clone(&server);
        let state = Arc::clone(&state);
        std::thread::spawn(move || loop {
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
        });
    }

    log::info!("Service started successfully on port {}", port);

    // 7. Keep the main thread alive
    loop {
        std::thread::sleep(std::time::Duration::from_secs(60));
    }
}

// 3. In main(), register with SCM
fn main() -> Result<(), windows_service::Error> {
    // Important: service_dispatcher::start() blocks until the service stops
    // and must be called FIRST
    service_dispatcher::start("MyServer", ffi_service_main)?;
    Ok(())
}
