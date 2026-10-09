// Hide the stale error banner when the page loads (e.g. back-navigation
        // from the console keeps it in the DOM via bfcache otherwise).
        window.addEventListener('pageshow', () => {
            const errorEl = document.getElementById('errorMessage');
            if (errorEl) { errorEl.style.display = 'none'; errorEl.textContent = ''; }
            const btn = document.getElementById('submitBtn');
            if (btn) { btn.disabled = false; btn.textContent = 'Sign In'; }
        });

        const getBasePath = () => {
            let p = window.location.pathname;
            if (p.endsWith('.html')) p = p.substring(0, p.lastIndexOf('/'));
            while (p.endsWith('/')) p = p.slice(0, -1);
            return p;
        };
        const BASE_PATH = getBasePath();
        const API_LOGIN = BASE_PATH ? (BASE_PATH + '/api/auth/login') : '/api/auth/login';

        function showError(msg) {
            const errorEl = document.getElementById('errorMessage');
            errorEl.textContent = msg;
            errorEl.style.display = 'block';
        }
        function clearError() {
            const errorEl = document.getElementById('errorMessage');
            errorEl.textContent = '';
            errorEl.style.display = 'none';
        }
        // Clear the banner as soon as the user edits any field.
        ['username', 'password', 'apiKey'].forEach((id) =>
            document.getElementById(id).addEventListener('input', clearError));
        document.getElementById('loginForm').addEventListener('submit', handleLogin);

        async function handleLogin(e) {
            e.preventDefault();
            const submitBtn = document.getElementById('submitBtn');
            const errorEl = document.getElementById('errorMessage');
            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value;
            const apiKey = document.getElementById('apiKey').value.trim();

            errorEl.style.display = 'none';
            submitBtn.disabled = true;
            submitBtn.textContent = 'Signing in...';

            // Client-side guard: an empty submit is a mistake, not an attack —
            // catch it here so it never burns a server-side brute-force strike.
            if (!password && !apiKey) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Sign In';
                showError('Enter your password — or an API key — to sign in.');
                document.getElementById('password').focus();
                return;
            }

            try {
                const res = await fetch(API_LOGIN, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password, apiKey })
                });

                if (res.ok) {
                    const data = await res.json().catch(() => ({}));
                    const headerCsrf = res.headers.get('X-CSRF-Token');
                    const csrf = data.csrfToken || headerCsrf;
                    if (csrf) {
                        sessionStorage.setItem('csrfToken', csrf);
                    }
                    if (data.session) {
                        sessionStorage.setItem('session', data.session);
                    }
                    if (apiKey) {
                        localStorage.setItem('apiKey', apiKey);
                    }
                    // Return the user to the deep-linked console view if ?next= is present
                    const next = new URLSearchParams(location.search).get('next');
                    const safeNext = (next && next.startsWith('/') && !next.startsWith('//')) ? next : null;
                    window.location.href = safeNext || (BASE_PATH ? (BASE_PATH + '/index.html') : 'index.html');
                } else {
                    const err = await res.json().catch(() => ({ message: 'Sign in failed' }));
                    if (res.status === 429) {
                        // Brute-force lockout: disable the form and count down
                        // using the server's Retry-After header (seconds).
                        const retryAfter = parseInt(res.headers.get('Retry-After'), 10);
                        startLockoutCountdown(Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : err.message);
                    } else {
                        showError(err.message || 'Invalid username or password. Check your credentials or API key.');
                    }
                }
            } catch (err) {
                // Network failure is NOT an auth result — never fake a redirect.
                console.error('Login request failed:', err);
                showError('Cannot reach the server. Check that EmbedJNoSQL is running and try again.');
            } finally {
                if (!lockoutTimer) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Sign In';
                }
            }
        }

        /* ---------------- lockout countdown ---------------- */
        let lockoutTimer = null;

        function startLockoutCountdown(secondsOrMessage) {
            const submitBtn = document.getElementById('submitBtn');
            // Server did not send a numeric Retry-After: show a static message.
            if (typeof secondsOrMessage === 'string') {
                showError(secondsOrMessage);
                return;
            }
            let remaining = secondsOrMessage;
            const tick = () => {
                if (remaining <= 0) {
                    clearInterval(lockoutTimer);
                    lockoutTimer = null;
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Sign In';
                    clearError();
                    document.getElementById('password').focus();
                    return;
                }
                submitBtn.disabled = true;
                submitBtn.textContent = `Locked — retry in ${remaining}s`;
                showError(`Too many failed attempts. You can try again in ${remaining} second${remaining === 1 ? '' : 's'}.`);
                remaining--;
            };
            clearError();
            tick();
            lockoutTimer = setInterval(tick, 1000);
        }
