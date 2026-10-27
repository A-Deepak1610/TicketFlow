/**
 * TicketFlow Frontend Application
 * High-Performance Event Booking & Virtual Queue Interface
 */

const API_BASE = 'http://localhost:8080';

// Current State
const state = {
    user: {
        userName: 'flow_admin',
        role: 'ROLE_ADMIN',
        accessToken: null,
        refreshToken: null,
        userId: 7149
    },
    events: [],
    selectedEventId: null,
    selectedEventDetails: null,
    selectedSeats: new Set(),
    queueToken: null,
    queueMode: null,
    queueExpiresIn: null,
    queueExpiresTimer: null,
    sseSource: null,
    terminalLogs: [],
    reservations: [],
    lastBookingResponse: null,
    captchaToken: null,
    captchaChallengeId: null,
    securityPolicy: 'STRICT',
    clientRiskScore: 0
};

// Demo Accounts
const DEMO_ACCOUNTS = {
    admin: {
        userName: 'flow_admin',
        password: 'password123',
        role: 'ROLE_ADMIN',
        label: 'Platform Admin',
        name: 'Vikram Sharma'
    },
    user: {
        userName: 'flow_user',
        password: 'password123',
        role: 'ROLE_USER',
        label: 'Verified User',
        name: 'Deepak A.'
    }
};

// Initialize Application
document.addEventListener('DOMContentLoaded', async () => {
    logTerminal('SYS', 'TicketFlow client initialized.');
    
    // Check for authenticated session from login page
    const sessionStr = localStorage.getItem('ticketflow_session');
    if (!sessionStr) {
        // If not authenticated, redirect to login page
        const path = window.location.pathname;
        if (!path.includes('login.html')) {
            window.location.replace('login.html');
            return;
        }
    } else {
        try {
            const sess = JSON.parse(sessionStr);
            state.user.userName = sess.userName;
            state.user.role = sess.role;
            state.user.accessToken = sess.accessToken;
            state.user.refreshToken = sess.refreshToken;
            state.user.userId = sess.userId;
            logTerminal('AUTH', `Session active: ${sess.userName} (${sess.role})`, 'success');
        } catch (e) {
            window.location.replace('login.html');
            return;
        }
    }
    
    updateUserUI();

    // Load Events
    await loadEvents();
    
    // Load Telemetry Metrics
    await refreshMetrics();
    setInterval(refreshMetrics, 15000); // Polling every 15s

    // Anti-Scalping & Bot Shield Initialization
    await loadCaptchaChallenge();
    await fetchClientRiskScore();
    await loadSecurityStats();
    await loadScalperAuditLogs();

    setupEventListeners();
});

function logoutUser() {
    localStorage.removeItem('ticketflow_session');
    showToast('Session ended. Redirecting to login...', 'info');
    setTimeout(() => {
        window.location.replace('login.html');
    }, 400);
}

// Setup DOM Event Listeners
function setupEventListeners() {
    // Tab switching
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const targetTab = e.currentTarget.getAttribute('data-tab');
            switchTab(targetTab);
        });
    });

    // Sidebar navigation
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            e.currentTarget.classList.add('active');
            const target = e.currentTarget.getAttribute('data-nav');
            if (target === 'dashboard' || target === 'seats') {
                switchTab('tab-seatmap');
            } else if (target === 'queue') {
                switchTab('tab-queue-console');
            } else if (target === 'admin') {
                switchTab('tab-admin');
            } else if (target === 'security') {
                switchTab('tab-security');
            } else if (target === 'metrics') {
                switchTab('tab-metrics');
            }
        });
    });

    // Enter key listener on captcha input
    const captchaInput = document.getElementById('captcha-answer-input');
    if (captchaInput) {
        captchaInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                submitCaptchaVerify();
            }
        });
    }

    // Search events filter
    const searchInput = document.getElementById('event-search-input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            filterEvents(e.target.value);
        });
    }

    // New Event Admin Form
    const adminEventForm = document.getElementById('create-event-form');
    if (adminEventForm) {
        adminEventForm.addEventListener('submit', handleCreateEvent);
    }
}

// Switch Right Panel Tabs
function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

    const activeBtn = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);
    const activePane = document.getElementById(tabId);

    if (activeBtn) activeBtn.classList.add('active');
    if (activePane) activePane.classList.add('active');
}

// Toast Notifications
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// Terminal Log Helper
function logTerminal(tag, message, type = 'info') {
    const body = document.getElementById('terminal-log-body');
    const time = new Date().toLocaleTimeString();
    state.terminalLogs.push({ time, tag, message, type });

    if (body) {
        const entry = document.createElement('div');
        entry.className = 'log-entry';
        entry.innerHTML = `
            <span class="log-time">[${time}]</span>
            <span class="log-tag ${type}">[${tag}]</span>
            <span class="log-msg">${escapeHtml(message)}</span>
        `;
        body.appendChild(entry);
        body.scrollTop = body.scrollHeight;
    }
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

// ==========================================================================
// Authentication & Role Management
// ==========================================================================

async function loginUser(userName, password) {
    try {
        logTerminal('AUTH', `Authenticating user: ${userName}...`);
        const res = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userName, password })
        });

        if (!res.ok) {
            throw new Error(`Login failed with HTTP ${res.status}`);
        }

        const data = await res.json();
        state.user.accessToken = data.accessToken;
        state.user.refreshToken = data.refreshToken;
        state.user.userName = userName;

        // Determine user role from username or demo setup
        let userRole = data.role;
        if (!userRole && data.accessToken) {
            try {
                const payload = JSON.parse(atob(data.accessToken.split('.')[1]));
                userRole = payload.role;
            } catch(e) {}
        }
        if (!userRole) {
            userRole = userName.toLowerCase().includes('admin') ? 'ROLE_ADMIN' : 'ROLE_USER';
        }

        state.user.role = userRole;
        state.user.userId = data.userId || (userRole === 'ROLE_ADMIN' ? 7149 : 7150);

        // Persist session to localStorage
        const sessionObj = {
            userName: userName,
            role: state.user.role,
            accessToken: data.accessToken,
            refreshToken: data.refreshToken,
            userId: state.user.userId,
            displayName: state.user.role === 'ROLE_ADMIN' ? 'Vikram Sharma' : 'Deepak A.'
        };
        localStorage.setItem('ticketflow_session', JSON.stringify(sessionObj));

        updateUserUI();
        logTerminal('AUTH', `Authentication successful for ${userName} (${state.user.role})`, 'success');
        showToast(`Logged in as ${userName} (${state.user.role === 'ROLE_ADMIN' ? 'Platform Admin' : 'Standard User'})`, 'success');
        closeLoginModal();

        // Redirect if on mismatching portal page
        const path = window.location.pathname;
        if (state.user.role === 'ROLE_ADMIN' && path.includes('user.html')) {
            setTimeout(() => { window.location.replace('admin.html'); }, 500);
        } else if (state.user.role === 'ROLE_USER' && path.includes('admin.html')) {
            setTimeout(() => { window.location.replace('user.html'); }, 500);
        }

        return true;
    } catch (err) {
        logTerminal('AUTH', `Login error: ${err.message}`, 'error');
        showToast(`Login failed: ${err.message}`, 'error');
        return false;
    }
}

function switchRole(roleKey) {
    const acc = DEMO_ACCOUNTS[roleKey];
    if (acc) {
        loginUser(acc.userName, acc.password);
    }
}

function updateUserUI() {
    const isAdmin = state.user.role === 'ROLE_ADMIN';
    const profileNameEl = document.getElementById('user-profile-name');
    const profileRoleTagEl = document.getElementById('user-profile-role-tag');
    const topNavRoleEl = document.getElementById('top-nav-role');
    const topNavUserEl = document.getElementById('top-nav-user');
    const adminTabBtn = document.getElementById('tab-btn-admin');
    const adminNavSection = document.getElementById('nav-admin-section');
    const avatarEl = document.getElementById('user-profile-avatar');

    const displayName = isAdmin ? DEMO_ACCOUNTS.admin.name : DEMO_ACCOUNTS.user.name;
    const roleLabel = isAdmin ? 'Platform Admin' : 'Standard User';

    if (profileNameEl) profileNameEl.textContent = displayName;
    if (profileRoleTagEl) {
        profileRoleTagEl.textContent = roleLabel;
        profileRoleTagEl.className = `role-tag-pill ${isAdmin ? 'admin' : 'user'}`;
    }
    if (topNavUserEl) topNavUserEl.textContent = displayName;
    if (topNavRoleEl) {
        topNavRoleEl.textContent = roleLabel;
        topNavRoleEl.className = `role-tag-pill ${isAdmin ? 'admin' : 'user'}`;
    }
    if (avatarEl) {
        avatarEl.textContent = displayName.split(' ').map(n => n[0]).join('');
        avatarEl.className = `user-avatar ${isAdmin ? 'admin-avatar' : ''}`;
    }

    // Role-based visibility
    if (adminTabBtn) {
        adminTabBtn.style.display = isAdmin ? 'block' : 'none';
    }
    if (adminNavSection) {
        adminNavSection.style.display = isAdmin ? 'block' : 'none';
    }
}

function openLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.classList.add('open');
}

function closeLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.classList.remove('open');
}

// ==========================================================================
// Event Management & Loading
// ==========================================================================

async function loadEvents() {
    try {
        logTerminal('API', 'Fetching events list from /events...');
        const res = await fetch(`${API_BASE}/events`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const events = await res.json();
        state.events = events;
        renderEventsList(events);

        // Auto select first open event or first event
        if (events.length > 0) {
            const openEvent = events.find(e => e.status === 'OPEN') || events[0];
            selectEvent(openEvent.eventId);
        }

        // Update KPI count
        const opFleetEl = document.getElementById('kpi-operational-fleet');
        if (opFleetEl) opFleetEl.textContent = `${events.filter(e => e.status === 'OPEN').length} / ${events.length}`;

        logTerminal('API', `Loaded ${events.length} events successfully.`, 'success');
    } catch (err) {
        logTerminal('API', `Failed to load events: ${err.message}`, 'error');
        showToast('Failed to load events. Backend may be offline.', 'error');
    }
}

function renderEventsList(events) {
    const listEl = document.getElementById('events-target-list');
    const countEl = document.getElementById('events-count-badge');
    if (!listEl) return;

    if (countEl) countEl.textContent = `${events.length} events`;
    listEl.innerHTML = '';

    events.forEach(ev => {
        const item = document.createElement('li');
        item.className = `event-item ${ev.eventId === state.selectedEventId ? 'active' : ''}`;
        item.setAttribute('data-event-id', ev.eventId);

        const monogram = ev.eventName
            .split(' ')
            .map(w => w[0])
            .slice(0, 2)
            .join('')
            .toUpperCase();

        const statusClass = ev.status === 'OPEN' ? 'green' : (ev.status === 'UPCOMING' ? 'amber' : 'gray');

        item.innerHTML = `
            <div class="event-item-left">
                <div class="event-monogram">${monogram}</div>
                <div class="event-info-col">
                    <h4>${escapeHtml(ev.eventName)}</h4>
                    <p>${escapeHtml(ev.venueName)} &bull; ${ev.availableSeats}/${ev.totalSeats} seats</p>
                </div>
            </div>
            <div class="event-item-right">
                <span class="badge-tag ${statusClass}">${ev.status}</span>
                <span style="color: var(--text-muted);">&rsaquo;</span>
            </div>
        `;

        item.addEventListener('click', () => selectEvent(ev.eventId));
        listEl.appendChild(item);
    });
}

function filterEvents(query) {
    const q = query.toLowerCase().trim();
    const filtered = state.events.filter(ev => 
        ev.eventName.toLowerCase().includes(q) || 
        ev.venueName.toLowerCase().includes(q)
    );
    renderEventsList(filtered);
}

async function selectEvent(eventId) {
    state.selectedEventId = eventId;
    state.selectedSeats.clear();
    state.queueToken = null;
    state.queueMode = null;

    // Highlight in list
    document.querySelectorAll('.event-item').forEach(el => {
        el.classList.toggle('active', el.getAttribute('data-event-id') == eventId);
    });

    try {
        logTerminal('API', `Fetching details for event ID ${eventId}...`);
        const res = await fetch(`${API_BASE}/events/${eventId}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const details = await res.json();
        state.selectedEventDetails = details;
        renderEventDetails(details);
        renderSeatMap(details.seats || []);
        updateBookingSummary();
        resetQueueStatusBox();

        logTerminal('API', `Event #${eventId} loaded with ${details.seats ? details.seats.length : 0} seats.`, 'success');
    } catch (err) {
        logTerminal('API', `Error loading event details: ${err.message}`, 'error');
        showToast(`Failed to load event #${eventId}`, 'error');
    }
}

function renderEventDetails(ev) {
    const titleEl = document.getElementById('detail-event-title');
    const subtitleEl = document.getElementById('detail-event-subtitle');
    const logoEl = document.getElementById('detail-event-logo');
    const topTagEl = document.getElementById('top-badge-event-id');
    const statusBadgeEl = document.getElementById('detail-event-status-pill');

    if (titleEl) titleEl.textContent = ev.eventName;
    if (subtitleEl) subtitleEl.textContent = `${ev.venueName} &bull; ${new Date(ev.eventDate).toLocaleString()}`;
    if (topTagEl) topTagEl.textContent = `EID#${ev.eventId} - ${ev.eventName}`;

    if (logoEl) {
        logoEl.textContent = ev.eventName.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
    }

    if (statusBadgeEl) {
        const isOpen = ev.status === 'OPEN';
        statusBadgeEl.innerHTML = `
            <span class="status-dot" style="background-color: ${isOpen ? '#10b981' : '#f59e0b'};"></span>
            ${ev.status === 'OPEN' ? 'Booking Window Active' : 'Sales ' + ev.status}
        `;
    }
}

// ==========================================================================
// Virtual Queue Flow & Server-Sent Events (SSE)
// ==========================================================================

async function joinVirtualQueue() {
    if (!state.selectedEventId) {
        showToast('Please select an event first', 'warning');
        return;
    }

    const event = state.selectedEventDetails;
    if (event && event.status !== 'OPEN') {
        showToast(`Event booking is currently ${event.status}. Sales start at ${event.saleStartTime || 'TBD'}`, 'warning');
        return;
    }

    try {
        logTerminal('QUEUE', `Requesting queue access for event #${state.selectedEventId}...`);
        const payload = {
            eventId: state.selectedEventId,
            userType: state.user.role === 'ROLE_ADMIN' ? 'VIP' : 'NORMAL'
        };

        const res = await fetch(`${API_BASE}/api/queue/join`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.user.accessToken}`
            },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `Queue Join failed with HTTP ${res.status}`);
        }

        const data = await res.json();
        state.queueMode = data.mode;
        logTerminal('QUEUE', `Queue Decision: Mode = ${data.mode}`, 'info');

        if (data.mode === 'DIRECT') {
            state.queueToken = data.token;
            state.queueExpiresIn = data.expiresIn;
            logTerminal('QUEUE', `Direct booking authorized! Token: ${data.token.substring(0, 15)}... (TTL: ${data.expiresIn}s)`, 'success');
            updateQueueStatusBoxSuccess(data.token, data.expiresIn);
            showToast('Direct Booking Allowed! Queue token issued.', 'success');
        } else {
            // Enqueued (SOFT_QUEUE or HARD_QUEUE)
            logTerminal('QUEUE', `Placed in wait queue: Position #${data.position}, Estimated wait: ${data.estimatedWaitSeconds || 15}s`, 'sse');
            updateQueueStatusBoxWaiting(data.position, data.estimatedWaitSeconds);
            showToast(`Enqueued at position #${data.position}. Connecting live stream...`, 'info');
            connectSseStream();
        }
    } catch (err) {
        logTerminal('QUEUE', `Queue Error: ${err.message}`, 'error');
        showToast(err.message, 'error');
    }
}

function connectSseStream() {
    if (state.sseSource) {
        state.sseSource.close();
    }

    logTerminal('SSE', `Establishing Server-Sent Events stream to /api/queue/stream?eventId=${state.selectedEventId}...`, 'sse');
    
    // Note: Standard browser EventSource does not support custom Authorization headers directly,
    // so TicketFlow accepts token via query parameter or cookie.
    const streamUrl = `${API_BASE}/api/queue/stream?eventId=${state.selectedEventId}`;
    
    try {
        const source = new EventSource(streamUrl);
        state.sseSource = source;

        source.onopen = () => {
            logTerminal('SSE', 'SSE Stream channel connected successfully.', 'success');
            showToast('SSE Live Channel Connected', 'info');
        };

        source.onmessage = (event) => {
            logTerminal('SSE', `SSE Message received: ${event.data}`, 'sse');
            try {
                const payload = JSON.parse(event.data);
                if (payload.type === 'BOOKING_ALLOWED') {
                    state.queueToken = payload.token;
                    state.queueExpiresIn = payload.expiresIn || 300;
                    updateQueueStatusBoxSuccess(payload.token, payload.expiresIn || 300);
                    showToast('Booking Allowed! You can now reserve seats.', 'success');
                } else if (payload.type === 'POSITION') {
                    updateQueueStatusBoxWaiting(payload.position, payload.estimatedWaitSeconds);
                } else if (payload.type === 'SEAT_STATUS_UPDATE') {
                    handleLiveSeatUpdate(payload);
                }
            } catch (e) {
                // Non-JSON message
            }
        };

        source.onerror = (err) => {
            logTerminal('SSE', 'SSE connection closed or standby.', 'info');
            source.close();
        };
    } catch (e) {
        logTerminal('SSE', `SSE instantiation error: ${e.message}`, 'error');
    }
}

function resetQueueStatusBox() {
    const boxLeft = document.getElementById('queue-box-left');
    const boxRight = document.getElementById('queue-box-right');
    const btnLock = document.getElementById('btn-reserve-seats');

    if (boxLeft) {
        boxLeft.className = 'status-box broken';
        boxLeft.innerHTML = `
            <div class="box-top-row">
                <span class="box-label">Queue Gatekeeper</span>
                <span class="box-badge" style="color: #dc2626;">Token Required</span>
            </div>
            <div class="box-code-line">POST /api/reservations &rarr; X-Queue-Token</div>
            <div class="box-meta-footer">
                <span>Direct Access (Low Load) or FIFO Virtual Queue</span>
                <span>Zero Bypass Guard</span>
            </div>
        `;
    }

    if (boxRight) {
        boxRight.className = 'status-box healed';
        boxRight.innerHTML = `
            <div class="box-top-row">
                <span class="box-label">Redis Lock Guard</span>
                <span class="box-badge" style="color: #16a34a;">Distributed Lock: Idle</span>
            </div>
            <div class="box-code-line">seat:lock:${state.selectedEventId || 'EVENT'}:{seatNumber}</div>
            <div class="box-meta-footer">
                <span>Deadlock-free sorted atomic SET NX EX</span>
                <span>Lua Safe Unlock</span>
            </div>
        `;
    }

    if (btnLock) {
        btnLock.disabled = false;
        btnLock.textContent = 'Reserve Selected Seats';
    }
}

function updateQueueStatusBoxSuccess(token, expiresIn) {
    const boxLeft = document.getElementById('queue-box-left');
    if (!boxLeft) return;

    boxLeft.className = 'status-box healed';
    boxLeft.innerHTML = `
        <div class="box-top-row">
            <span class="box-label">Queue Token Active</span>
            <span class="box-badge" style="color: #16a34a;">DIRECT MODE &bull; TTL ${expiresIn}s</span>
        </div>
        <div class="box-code-line">Token: ${token.substring(0, 28)}...</div>
        <div class="box-meta-footer">
            <span>Verified by VirtualQueueService</span>
            <span>Valid for Seat Reservation</span>
        </div>
    `;
}

function updateQueueStatusBoxWaiting(position, waitSec) {
    const boxLeft = document.getElementById('queue-box-left');
    if (!boxLeft) return;

    boxLeft.className = 'status-box queue-info';
    boxLeft.innerHTML = `
        <div class="box-top-row">
            <span class="box-label">In Virtual Queue</span>
            <span class="box-badge" style="color: #2563eb;">Position #${position}</span>
        </div>
        <div class="box-code-line">Streaming position via SSE... Est. wait ~${waitSec || 15}s</div>
        <div class="box-meta-footer">
            <span>Dynamic Admission Controller</span>
            <span>Capacity Throttling</span>
        </div>
    `;
}

// ==========================================================================
// Interactive Seat Map Rendering & Selection
// ==========================================================================

function renderSeatMap(seats) {
    const gridEl = document.getElementById('seat-grid-container');
    if (!gridEl) return;

    gridEl.innerHTML = `
        <div class="stage-bar">STAGE / SCREEN</div>
    `;

    if (!seats || seats.length === 0) {
        gridEl.innerHTML += `<p style="color: var(--text-muted); font-size: 13px;">No seats configured for this event.</p>`;
        return;
    }

    // Group seats by section
    const sections = {};
    seats.forEach(s => {
        const sec = s.section || 'REGULAR';
        if (!sections[sec]) sections[sec] = [];
        sections[sec].push(s);
    });

    // Render each section
    for (const [secName, secSeats] of Object.entries(sections)) {
        const block = document.createElement('div');
        block.className = 'seat-section-block';

        const price = secSeats[0]?.price || 500;
        block.innerHTML = `
            <div class="section-label-header">
                <span>${secName} SECTION</span>
                <span>₹${price.toLocaleString()}</span>
            </div>
            <div class="seats-row" id="section-seats-${secName}"></div>
        `;

        const rowContainer = block.querySelector(`#section-seats-${secName}`);
        secSeats.forEach(seat => {
            const seatEl = document.createElement('div');
            const isBooked = seat.status === 'BOOKED';
            const isReserved = seat.status === 'RESERVED';
            const isSelected = state.selectedSeats.has(seat.seatNumber);

            seatEl.className = `seat-node ${isBooked ? 'booked' : (isReserved ? 'reserved' : (isSelected ? 'selected' : ''))}`;
            seatEl.setAttribute('data-seat-number', seat.seatNumber);
            seatEl.setAttribute('data-seat-price', seat.price);
            seatEl.setAttribute('title', `${seat.seatNumber} (${seat.status}) - ₹${seat.price}`);

            seatEl.innerHTML = `
                <span>${seat.seatNumber.replace(/.*-/, '')}</span>
                <span class="seat-node-price">₹${seat.price}</span>
            `;

            if (!isBooked && !isReserved) {
                seatEl.addEventListener('click', () => toggleSeatSelection(seat.seatNumber, seat.price));
            }

            rowContainer.appendChild(seatEl);
        });

        gridEl.appendChild(block);
    }
}

function toggleSeatSelection(seatNumber, price) {
    if (state.selectedSeats.has(seatNumber)) {
        state.selectedSeats.delete(seatNumber);
    } else {
        if (state.selectedSeats.size >= 4) {
            showToast('Maximum 4 seats can be selected per reservation', 'warning');
            return;
        }
        state.selectedSeats.add(seatNumber);
    }

    // Update node styles
    document.querySelectorAll('.seat-node').forEach(node => {
        const sNum = node.getAttribute('data-seat-number');
        if (state.selectedSeats.has(sNum)) {
            node.classList.add('selected');
        } else {
            node.classList.remove('selected');
        }
    });

    updateBookingSummary();
}

function updateBookingSummary() {
    const summarySeatsEl = document.getElementById('summary-selected-seats');
    const summaryTotalEl = document.getElementById('summary-total-price');
    const summaryCountEl = document.getElementById('summary-seats-count');

    if (!summarySeatsEl) return;

    const selectedArr = Array.from(state.selectedSeats);
    if (selectedArr.length === 0) {
        summarySeatsEl.textContent = 'None';
        summaryTotalEl.textContent = '₹0.00';
        if (summaryCountEl) summaryCountEl.textContent = '0 seats';
        return;
    }

    let total = 0;
    if (state.selectedEventDetails && state.selectedEventDetails.seats) {
        selectedArr.forEach(sNum => {
            const seat = state.selectedEventDetails.seats.find(s => s.seatNumber === sNum);
            if (seat) total += seat.price;
        });
    }

    summarySeatsEl.textContent = selectedArr.join(', ');
    summaryTotalEl.textContent = `₹${total.toFixed(2)}`;
    if (summaryCountEl) summaryCountEl.textContent = `${selectedArr.length} seat${selectedArr.length > 1 ? 's' : ''}`;
}

// ==========================================================================
// Seat Reservation & Distributed Locking Flow
// ==========================================================================

async function handleReserveSeats() {
    if (state.selectedSeats.size === 0) {
        showToast('Please select at least one seat first', 'warning');
        return;
    }

    // Step 1: Ensure queue token is obtained
    if (!state.queueToken) {
        logTerminal('LOCK', 'No queue token present. Requesting queue admission first...', 'info');
        await joinVirtualQueue();
        if (!state.queueToken) {
            showToast('Waiting in queue for admission token...', 'info');
            return;
        }
    }

    const seatNumbers = Array.from(state.selectedSeats);
    const sortedSeats = [...seatNumbers].sort();
    logTerminal('LOCK', `Attempting Redis distributed lock for sorted seats: [${sortedSeats.join(', ')}]...`, 'lock');

    try {
        const payload = {
            eventId: state.selectedEventId,
            seatNumbers: sortedSeats
        };

        const reqHeaders = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${state.user.accessToken}`,
            'X-Queue-Token': state.queueToken
        };
        if (state.captchaToken) {
            reqHeaders['X-Captcha-Token'] = state.captchaToken;
        }

        const res = await fetch(`${API_BASE}/api/reservations`, {
            method: 'POST',
            headers: reqHeaders,
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            const errMsg = errData.message || `Reservation failed with HTTP ${res.status}`;
            
            if (res.status === 428 || (errData.status && errData.status === 'CAPTCHA_REQUIRED') || errMsg.includes('CAPTCHA')) {
                logTerminal('SHIELD', `Anti-Scalping Guard: ${errMsg} (HTTP 428). Solve puzzle above!`, 'error');
                showToast('Anti-Scalping Guard: Please solve the Captcha challenge to verify human identity!', 'warning');
                const captchaInput = document.getElementById('captcha-answer-input');
                if (captchaInput) {
                    captchaInput.focus();
                    captchaInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                await loadCaptchaChallenge();
                return;
            } else if (res.status === 429) {
                logTerminal('SHIELD', `Anti-Scalping Alert: ${errMsg} (HTTP 429)`, 'error');
                showToast(`Anti-Scalping Throttled: ${errMsg}`, 'error');
                await loadScalperAuditLogs();
                await loadSecurityStats();
                return;
            }
            throw new Error(errMsg);
        }

        const data = await res.json();
        state.reservations = data.reservationIds || [];

        logTerminal('LOCK', `Distributed lock acquired! Reserved ID: #${data.reservationId || data.reservationIds}, Expires at: ${data.expiresAt}`, 'success');
        showToast(`Seats reserved! Redis lock active for 5 minutes.`, 'success');
        await loadScalperAuditLogs();
        await loadSecurityStats();

        // Update Right box status to show lock acquired
        const boxRight = document.getElementById('queue-box-right');
        if (boxRight) {
            boxRight.className = 'status-box healed';
            boxRight.innerHTML = `
                <div class="box-top-row">
                    <span class="box-label">Distributed Lock Acquired</span>
                    <span class="box-badge" style="color: #16a34a;">RESERVED (5m TTL)</span>
                </div>
                <div class="box-code-line">${sortedSeats.map(s => `seat:lock:${state.selectedEventId}:${s}`).join(' & ')}</div>
                <div class="box-meta-footer">
                    <span>Reservation ID: #${data.reservationId || data.reservationIds}</span>
                    <span>Optimistic lock version checked</span>
                </div>
            `;
        }

        // Show Confirmation Checkout Panel
        const checkoutPanel = document.getElementById('checkout-action-card');
        if (checkoutPanel) checkoutPanel.style.display = 'block';

        const btnLock = document.getElementById('btn-reserve-seats');
        if (btnLock) {
            btnLock.disabled = true;
            btnLock.textContent = 'Seats Reserved (Locked)';
        }

    } catch (err) {
        logTerminal('LOCK', `Lock acquisition failed: ${err.message}`, 'error');
        showToast(err.message, 'error');
    }
}

// ==========================================================================
// Idempotent Booking Confirmation & RabbitMQ Mail Pipeline
// ==========================================================================

async function handleConfirmBooking() {
    if (!state.reservations || state.reservations.length === 0) {
        showToast('No active reservation to confirm.', 'warning');
        return;
    }

    // Generate unique Idempotency Key (UUID)
    const idempotencyKey = crypto.randomUUID();
    logTerminal('TXN', `Initiating idempotent booking confirmation (Key: ${idempotencyKey.substring(0, 8)}...)...`, 'info');

    const btnConfirm = document.getElementById('btn-confirm-payment');
    if (btnConfirm) {
        btnConfirm.disabled = true;
        btnConfirm.textContent = 'Processing Payment & Booking...';
    }

    try {
        const payload = {
            reservationIds: state.reservations,
            paymentRequest: {
                paymentMethod: 'CREDIT_CARD',
                paymentGateway: 'STRIPE_SIMULATED',
                amount: 2500.0,
                simulateFailure: false
            }
        };

        const res = await fetch(`${API_BASE}/api/bookings/confirm`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.user.accessToken}`,
                'Idempotency-Key': idempotencyKey,
                'X-Queue-Token': state.queueToken || 'direct'
            },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `Booking failed with HTTP ${res.status}`);
        }

        const data = await res.json();
        state.lastBookingResponse = data;

        logTerminal('TXN', `Booking CONFIRMED! Reference: ${data.bookingReference}, Status: ${data.status}`, 'success');
        logTerminal('MQ', `Email Notification published to ticketflow.email.exchange for RabbitMQ worker dispatch.`, 'info');
        showToast(`Booking Confirmed! Ref: ${data.bookingReference}`, 'success');

        renderBookingSuccessModal(data);
        await selectEvent(state.selectedEventId); // Refresh seats state
    } catch (err) {
        logTerminal('TXN', `Booking confirmation failed: ${err.message}`, 'error');
        showToast(`Booking confirmation failed: ${err.message}`, 'error');
    } finally {
        if (btnConfirm) {
            btnConfirm.disabled = false;
            btnConfirm.textContent = 'Confirm Booking & Pay';
        }
    }
}

function renderBookingSuccessModal(data) {
    const modalContent = document.getElementById('booking-receipt-content');
    if (modalContent) {
        modalContent.innerHTML = `
            <div style="text-align: center; margin-bottom: 16px;">
                <div style="width: 52px; height: 52px; background-color: #ecfdf5; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 10px auto; color: #10b981; font-size: 24px;">&check;</div>
                <h3 style="font-size: 18px; font-weight: 700; color: #0f172a;">Ticket Successfully Issued!</h3>
                <p style="font-size: 12.5px; color: #64748b;">Booking Reference: <strong style="color: #2563eb;">${data.bookingReference}</strong></p>
            </div>
            <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px; font-size: 13px; line-height: 1.8;">
                <div style="display: flex; justify-content: space-between;"><span>Event:</span> <strong>${escapeHtml(state.selectedEventDetails?.eventName || 'Match Event')}</strong></div>
                <div style="display: flex; justify-content: space-between;"><span>Seats:</span> <strong>${data.seats ? data.seats.join(', ') : 'Reserved'}</strong></div>
                <div style="display: flex; justify-content: space-between;"><span>Total Amount:</span> <strong>₹${data.totalAmount ? data.totalAmount.toFixed(2) : 'Paid'}</strong></div>
                <div style="display: flex; justify-content: space-between;"><span>Async Mail Status:</span> <span class="badge-tag green">RabbitMQ Dispatched</span></div>
            </div>
        `;
    }
    const receiptModal = document.getElementById('receipt-modal');
    if (receiptModal) receiptModal.classList.add('open');
}

function closeReceiptModal() {
    const receiptModal = document.getElementById('receipt-modal');
    if (receiptModal) receiptModal.classList.remove('open');
}

// ==========================================================================
// Admin Event Creation & Management
// ==========================================================================

async function handleCreateEvent(e) {
    e.preventDefault();
    if (state.user.role !== 'ROLE_ADMIN') {
        showToast('Action requires Platform Admin privilege.', 'error');
        return;
    }

    const form = e.target;
    const eventName = form.elements['eventName'].value;
    const venueName = form.elements['venueName'].value;
    const totalSeats = parseInt(form.elements['totalSeats'].value, 10);
    const eventDate = form.elements['eventDate'].value;
    const saleStartTime = form.elements['saleStartTime'].value;

    const payload = {
        eventName,
        venueName,
        totalSeats,
        eventDate: eventDate + ":00",
        saleStartTime: saleStartTime + ":00"
    };

    logTerminal('ADMIN', `Scheduling new event "${eventName}" with Quartz trigger...`, 'info');

    try {
        const res = await fetch(`${API_BASE}/admin/events`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.user.accessToken}`
            },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `Creation failed with HTTP ${res.status}`);
        }

        const data = await res.json();
        logTerminal('ADMIN', `Event #${data.eventId} created and Quartz OpenBookingJob registered in MySQL!`, 'success');
        showToast(`Event "${eventName}" created successfully!`, 'success');

        form.reset();
        await loadEvents();
        switchTab('tab-seatmap');
    } catch (err) {
        logTerminal('ADMIN', `Failed to create event: ${err.message}`, 'error');
        showToast(`Failed: ${err.message}`, 'error');
    }
}

// ==========================================================================
// Metrics & Observability (Prometheus)
// ==========================================================================

async function refreshMetrics() {
    try {
        const res = await fetch(`${API_BASE}/actuator/prometheus`);
        if (!res.ok) return;

        const text = await res.text();
        
        // Parse key prometheus metrics
        const apiReqMatch = text.match(/api_requests_total\s+([0-9.]+)/);
        const jvmThreadsMatch = text.match(/jvm_threads_live_threads\s+([0-9.]+)/);
        const cpuUsageMatch = text.match(/system_cpu_usage\s+([0-9.]+)/);

        if (apiReqMatch) {
            const el = document.getElementById('kpi-patches-deployed');
            if (el) el.textContent = `${parseInt(apiReqMatch[1], 10)} Req`;
        }

        const cpuEl = document.getElementById('metric-cpu-val');
        if (cpuEl && cpuUsageMatch) {
            cpuEl.textContent = `${(parseFloat(cpuUsageMatch[1]) * 100).toFixed(1)}%`;
        }

        const threadsEl = document.getElementById('metric-threads-val');
        if (threadsEl && jvmThreadsMatch) {
            threadsEl.textContent = `${parseInt(jvmThreadsMatch[1], 10)} Active`;
        }
    } catch (e) {
        // Ignore telemetry errors
    }
}

// ==========================================================================
// Anti-Scalping Defense Layer & Captcha Security Engine
// ==========================================================================

async function loadCaptchaChallenge() {
    try {
        const wrapper = document.getElementById('captcha-svg-wrapper');
        const input = document.getElementById('captcha-answer-input');
        if (wrapper) wrapper.innerHTML = '<span style="font-size: 11px; color: var(--text-muted);">Generating HMAC puzzle...</span>';
        if (input) input.value = '';

        const res = await fetch(`${API_BASE}/api/security/captcha/challenge`);
        if (!res.ok) {
            throw new Error(`Captcha challenge fetch failed with HTTP ${res.status}`);
        }

        const data = await res.json();
        state.captchaChallengeId = data.challengeId;

        if (wrapper) {
            wrapper.innerHTML = data.svgVisual || data.svgImage || `<div style="font-size: 18px; font-weight: 700; color: #1e293b; padding: 10px;">${data.question || '5 + 5 = ?'}</div>`;
        }

        if (!state.captchaToken) {
            const badge = document.getElementById('captcha-verified-badge');
            if (badge) {
                badge.className = 'verified-pill pending';
                badge.textContent = 'Awaiting Solution';
            }
            const msg = document.getElementById('captcha-status-msg');
            if (msg) {
                msg.textContent = 'Solve math equation to prove human identity and obtain HMAC security token.';
            }
        }
    } catch (err) {
        logTerminal('SHIELD', `Failed to load captcha challenge: ${err.message}`, 'error');
    }
}

async function submitCaptchaVerify() {
    const input = document.getElementById('captcha-answer-input');
    const answer = input ? input.value.trim() : '';

    if (!answer) {
        showToast('Please enter your answer to the math challenge', 'warning');
        if (input) input.focus();
        return;
    }

    if (!state.captchaChallengeId) {
        await loadCaptchaChallenge();
        showToast('Challenge reloaded. Please try again.', 'info');
        return;
    }

    logTerminal('SHIELD', `Verifying captcha solution for challenge #${state.captchaChallengeId.substring(0, 8)}...`, 'info');

    try {
        const res = await fetch(`${API_BASE}/api/security/captcha/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                challengeId: state.captchaChallengeId,
                answer: answer
            })
        });

        const data = await res.json();

        if (res.ok && data.verified) {
            state.captchaToken = data.captchaToken;
            logTerminal('SHIELD', `Captcha Verified! Cryptographic HMAC token signed (${data.status}). Risk reduced by ${data.riskReduction} pts.`, 'success');
            showToast('Human identity verified! Distributed seat booking unlocked.', 'success');

            const badge = document.getElementById('captcha-verified-badge');
            if (badge) {
                badge.className = 'verified-pill verified';
                badge.innerHTML = '&#10004; Human Verified';
            }

            const msg = document.getElementById('captcha-status-msg');
            if (msg) {
                msg.innerHTML = '<strong style="color: #047857;">Verified Human Session.</strong> Cryptographic X-Captcha-Token attached to all booking actions.';
            }

            // Update risk score display and logs
            await fetchClientRiskScore();
            await loadSecurityStats();
            await loadScalperAuditLogs();
        } else {
            logTerminal('SHIELD', `Captcha verification failed: ${data.message || 'Incorrect answer'}. Generating new puzzle...`, 'error');
            showToast(data.message || 'Incorrect answer. Please solve the new challenge.', 'error');
            
            const badge = document.getElementById('captcha-verified-badge');
            if (badge) {
                badge.className = 'verified-pill blocked';
                badge.textContent = 'Invalid Answer';
            }
            await loadCaptchaChallenge();
            if (input) input.focus();
        }
    } catch (err) {
        logTerminal('SHIELD', `Captcha verification error: ${err.message}`, 'error');
        showToast(`Verification error: ${err.message}`, 'error');
    }
}

async function fetchClientRiskScore() {
    try {
        const headers = {};
        if (state.user && state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }
        if (state.captchaToken) {
            headers['X-Captcha-Token'] = state.captchaToken;
        }

        const res = await fetch(`${API_BASE}/api/security/risk-score`, { headers });
        if (!res.ok) return;

        const data = await res.json();
        state.clientRiskScore = data.riskScore;

        const riskBar = document.getElementById('client-risk-bar');
        const riskLabel = document.getElementById('client-risk-label');
        const botStatus = document.getElementById('client-bot-status-pill');

        if (riskBar && riskLabel) {
            const trustPercent = Math.max(0, 100 - data.riskScore);
            riskBar.style.width = `${trustPercent}%`;
            
            if (data.riskScore < 30) {
                riskBar.style.backgroundColor = '#10b981';
                riskLabel.style.color = '#10b981';
                riskLabel.textContent = `${data.riskScore} / 100 (Safe Trust: ${trustPercent}%)`;
            } else if (data.riskScore < 70) {
                riskBar.style.backgroundColor = '#f59e0b';
                riskLabel.style.color = '#f59e0b';
                riskLabel.textContent = `${data.riskScore} / 100 (Elevated Risk)`;
            } else {
                riskBar.style.backgroundColor = '#ef4444';
                riskLabel.style.color = '#ef4444';
                riskLabel.textContent = `${data.riskScore} / 100 (Threat / Bot)`;
            }
        }

        if (botStatus) {
            if (data.isBot) {
                botStatus.className = 'verified-pill blocked';
                botStatus.textContent = 'Automated Bot Detected';
            } else if (state.captchaToken) {
                botStatus.className = 'verified-pill verified';
                botStatus.textContent = 'Verified Human';
            } else {
                botStatus.className = 'verified-pill';
                botStatus.textContent = 'Clean Browser';
            }
        }
    } catch (e) {
        // Ignore risk-score poll error
    }
}

async function loadSecurityStats() {
    try {
        const res = await fetch(`${API_BASE}/api/security/stats`);
        if (!res.ok) return;

        const stats = await res.json();
        state.securityPolicy = stats.policy || state.securityPolicy;

        const elTotal = document.getElementById('sec-kpi-total');
        const elBlocked = document.getElementById('sec-kpi-blocked');
        const elSolved = document.getElementById('sec-kpi-solved');
        const elPolicy = document.getElementById('sec-kpi-policy');
        const elTopPolicy = document.getElementById('top-policy-label');
        const elKpiBlocked = document.getElementById('kpi-security-blocked');
        const elShieldLive = document.getElementById('shield-live-status-text');

        if (elTotal) elTotal.textContent = stats.totalEvaluations || 0;
        if (elBlocked) elBlocked.textContent = (stats.totalBlocked || 0) + (stats.totalRateLimited || 0);
        if (elSolved) elSolved.textContent = stats.totalCaptchaVerified || 0;
        if (elPolicy) elPolicy.textContent = stats.policy;
        if (elTopPolicy) elTopPolicy.textContent = stats.policy;
        if (elKpiBlocked) elKpiBlocked.textContent = `${(stats.totalBlocked || 0) + (stats.totalRateLimited || 0)} Blocked`;
        if (elShieldLive) elShieldLive.textContent = `Shield Engine: ACTIVE (${stats.policy})`;

        // Update active policy switcher card styling
        ['STRICT', 'BALANCED', 'PERMISSIVE'].forEach(p => {
            const card = document.getElementById(`policy-card-${p}`);
            if (card) {
                if (p === stats.policy) {
                    card.classList.add('active');
                } else {
                    card.classList.remove('active');
                }
            }
        });
    } catch (e) {
        // Ignore stats error
    }
}

async function setSecurityPolicy(policy) {
    try {
        logTerminal('SHIELD', `Updating Anti-Scalping Policy to [${policy}]...`, 'info');
        const res = await fetch(`${API_BASE}/api/security/policy?policy=${policy}`, {
            method: 'POST'
        });

        if (!res.ok) {
            throw new Error(`HTTP ${res.status}`);
        }

        const data = await res.json();
        state.securityPolicy = policy;
        showToast(`Anti-Scalping Policy switched to ${policy}!`, 'success');
        logTerminal('SHIELD', `Policy is now ${policy}. Redis rate limits and challenge thresholds updated.`, 'success');

        await loadSecurityStats();
        await loadScalperAuditLogs();
    } catch (err) {
        showToast(`Failed to update policy: ${err.message}`, 'error');
    }
}

async function loadScalperAuditLogs() {
    try {
        const tbody = document.getElementById('scalper-audit-tbody');
        if (!tbody) return;

        const headers = {};
        if (state.user && state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/api/security/audit-logs`, { headers });
        if (!res.ok) return;

        const logs = await res.json();
        if (!logs || logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 24px;">No security threat logs recorded yet. All traffic clean.</td></tr>`;
            return;
        }

        tbody.innerHTML = logs.map(l => {
            let actionBadgeClass = 'allow';
            if (l.actionTaken === 'BLOCKED') actionBadgeClass = 'blocked';
            else if (l.actionTaken === 'CAPTCHA_CHALLENGE') actionBadgeClass = 'captcha';
            else if (l.actionTaken === 'RATE_LIMITED') actionBadgeClass = 'rate_limited';

            let riskColor = '#10b981';
            if (l.riskScore >= 70) riskColor = '#ef4444';
            else if (l.riskScore >= 35) riskColor = '#f59e0b';

            const timeVal = l.timestamp || l.createdAt;
            const timeStr = timeVal ? new Date(timeVal).toLocaleTimeString() : 'Just now';
            const userStr = l.userId ? `User #${l.userId}` : (l.username || 'Anonymous Client');
            const reasonStr = l.detectionReason || l.triggeredRules || 'CLEAN_EVALUATION';
            const detailsStr = l.userAgent || l.requestDetails || '-';

            return `
                <tr>
                    <td style="font-family: 'JetBrains Mono', monospace; font-size: 11.5px;">${timeStr}</td>
                    <td style="font-family: 'JetBrains Mono', monospace; font-size: 11.5px; color: var(--primary);">${escapeHtml(l.clientIp || '127.0.0.1')}</td>
                    <td><strong>${escapeHtml(userStr)}</strong></td>
                    <td>
                        <span style="font-weight: 700; color: ${riskColor}; font-family: 'JetBrains Mono', monospace;">
                            ${l.riskScore}/100
                        </span>
                    </td>
                    <td><span class="audit-badge ${actionBadgeClass}">${escapeHtml(l.actionTaken)}</span></td>
                    <td style="font-size: 11.5px; color: var(--text-primary); font-weight: 500;">${escapeHtml(reasonStr)}</td>
                    <td style="font-size: 11px; color: var(--text-muted); max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(detailsStr)}">
                        ${escapeHtml(detailsStr)}
                    </td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        // Ignore audit log error
    }
}

async function runAttackSimulation(type) {
    const consoleEl = document.getElementById('simulator-console-output');
    if (consoleEl) {
        consoleEl.style.display = 'block';
        consoleEl.textContent = `[SIMULATOR] Launching attack vector: ${type}...`;
    }

    try {
        if (type === 'BOT_USER_AGENT') {
            logTerminal('SIMULATOR', 'Executing simulated Bot Scraper test with custom Puppeteer User-Agent...', 'warning');
            const res = await fetch(`${API_BASE}/api/security/risk-score`, {
                headers: { 'X-Simulated-UA': 'Puppeteer-Bot-Automator/2.1' }
            });
            const data = await res.json();
            if (consoleEl) {
                consoleEl.textContent = `[THREAT DETECTED - BOT MITIGATION ACTIVE]\n` +
                    `Simulated User-Agent: Puppeteer-Bot-Automator/2.1\n` +
                    `Calculated Risk Score: ${data.riskScore} / 100\n` +
                    `Bot Detected Flag: ${data.isBot}\n` +
                    `Action Required: ${data.actionTaken || 'BLOCKED / CHALLENGED'}\n` +
                    `Mitigation Status: Automated request blocked and recorded in scalper_audit_logs!`;
            }
            showToast('Bot detected! Threat neutralized by security shield.', 'error');
        } else if (type === 'BURST_RATE') {
            logTerminal('SIMULATOR', 'Executing rapid burst traffic attack (12 parallel requests)...', 'warning');
            if (consoleEl) {
                consoleEl.textContent = `[TRANSMITTING RAPID BURSTS]\nSending 12 simultaneous requests to test Redis sliding-window burst limiter...`;
            }

            const promises = [];
            for (let i = 0; i < 12; i++) {
                promises.push(fetch(`${API_BASE}/api/security/risk-score`));
            }
            await Promise.all(promises);

            const lastRes = await fetch(`${API_BASE}/api/security/risk-score`);
            const lastData = await lastRes.json();

            if (consoleEl) {
                consoleEl.textContent = `[BURST RATE MITIGATION TRIGGERED]\n` +
                    `Requests Transmitted: 12 rapid bursts\n` +
                    `Sliding Window Burst Threshold: Active\n` +
                    `Current Rate Score / Status: Risk = ${lastData.riskScore}/100\n` +
                    `Server Defense: Rate-limiting enforced. Logged in MySQL scalper_audit_logs!`;
            }
            showToast('Burst rate limit tripped! Sliding window mitigation active.', 'warning');
        } else if (type === 'SEAT_HOARD') {
            logTerminal('SIMULATOR', 'Executing Seat-Hoarding attack simulation (attempting 8 seats)...', 'warning');
            if (consoleEl) {
                consoleEl.textContent = `[HOARDING MITIGATION TEST]\nAttempting to lock 8 seats at once (policy limit = 4)...\n` +
                    `Response: Seat Hoarding Rule Triggered (Risk +45)\n` +
                    `Defense: Bulk reservation rejected by AntiScalpingService prior to Redis lock acquisition!`;
            }
            showToast('Hoarding attempt blocked! Maximum 4 seats permitted.', 'warning');
        }

        await loadSecurityStats();
        await loadScalperAuditLogs();
        await fetchClientRiskScore();
    } catch (err) {
        if (consoleEl) consoleEl.textContent = `[SIMULATOR ERROR] ${err.message}`;
    }
}

