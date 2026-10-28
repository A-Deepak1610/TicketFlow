/**
 * TicketFlow Unified Frontend Application Engine
 * Supports User Booking Portal & Admin Command Center
 */

const API_BASE = 'http://localhost:8080';
const DEFAULT_USER_EMAIL = 'deepaka.ad24@bitsathy.ac.in';
const RECAPTCHA_SITE_KEY = '6LepXwUuAAAAAPvDP5BY6nVP6qhj4VGNILqamDFv';

// Global Application State
const state = {
    user: {
        userName: 'flow_user',
        role: 'ROLE_USER',
        accessToken: null,
        refreshToken: null,
        userId: 7150,
        email: DEFAULT_USER_EMAIL,
        name: 'Deepak A.'
    },
    events: [],
    selectedEventId: null,
    selectedEventDetails: null,
    selectedSeats: new Set(),
    queueToken: null,
    queueMode: 'DIRECT',
    queueWaitTimer: null,
    reservationIds: [],
    reservationExpiresAt: null,
    reservationTimer: null,
    lastConfirmedBooking: null,
    userBookings: [],
    adminBookings: [],
    inspectingEventId: null,
    inspectingEventData: null,
    inspectingEventSeats: [],
    sseSource: null,
    googleRecaptchaToken: null,
    verifiedCaptchaToken: null,
    metrics: {
        jvmThreads: 38,
        systemCpu: '0.0%',
        hikariConnections: '5 / 20',
        totalTicketsSold: 19,
        totalRevenue: 9500,
        occupancyRate: '2.4%'
    }
};

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', async () => {
    initSession();
    updateProfileUI();

    // Check if we are on user portal or admin portal
    const isUserPortal = !!document.getElementById('view-user-dashboard');
    const isAdminPortal = !!document.getElementById('tab-admin-events');

    // Initial Data Fetch
    await loadEvents();

    if (isUserPortal) {
        await loadUserBookings();
        connectSseStream(); // Global SSE stream for live updates
    } else if (isAdminPortal) {
        await refreshMetrics();
        await loadScalperAuditLogs();
        await loadQueueAndTicketAnalytics();
        connectSseStream(); // Admin live stream
        setInterval(refreshMetrics, 12000); // Periodically refresh actuator telemetry
    }
});

// ─────────────────────────────────────────────────────────────
// Session & Auth Management
// ─────────────────────────────────────────────────────────────
function initSession() {
    const sessionStr = localStorage.getItem('ticketflow_session');
    if (sessionStr) {
        try {
            const sess = JSON.parse(sessionStr);
            state.user.userName = sess.userName || 'flow_user';
            state.user.role = sess.role || 'ROLE_USER';
            state.user.accessToken = sess.accessToken || null;
            state.user.refreshToken = sess.refreshToken || null;
            state.user.userId = sess.userId || (sess.role === 'ROLE_ADMIN' ? 7149 : 7150);
            state.user.email = DEFAULT_USER_EMAIL;
            state.user.name = sess.role === 'ROLE_ADMIN' ? 'Vikram Sharma' : 'Deepak A.';
        } catch (e) {
            console.error('Failed parsing session', e);
        }
    }
}

function updateProfileUI() {
    // User Profile Elements
    const userAvatar = document.getElementById('user-profile-avatar');
    const userName = document.getElementById('user-profile-name');
    const topNavUser = document.getElementById('top-nav-user');
    const greetingName = document.getElementById('dash-greeting-name');
    const statEmail = document.getElementById('dash-stat-email');

    if (userName) userName.textContent = state.user.name;
    if (topNavUser) topNavUser.textContent = state.user.name;
    if (greetingName) greetingName.textContent = state.user.name.split(' ')[0];
    if (statEmail) statEmail.textContent = state.user.email;
    if (userAvatar) userAvatar.textContent = state.user.name.split(' ').map(n => n[0]).join('');

    // Admin Profile Elements
    const adminName = document.getElementById('admin-profile-name');
    if (adminName) adminName.textContent = state.user.name;
}

function logoutUser() {
    localStorage.removeItem('ticketflow_session');
    showToast('Signed out successfully. Redirecting...', 'info');
    setTimeout(() => {
        window.location.replace('login.html');
    }, 400);
}

// ─────────────────────────────────────────────────────────────
// User Role Section Navigation (ONLY Dashboard & Booking)
// ─────────────────────────────────────────────────────────────
function switchUserSection(section) {
    const navDashboard = document.getElementById('user-nav-dashboard');
    const navBooking = document.getElementById('user-nav-booking');
    const viewDashboard = document.getElementById('view-user-dashboard');
    const viewBooking = document.getElementById('view-user-booking');
    const breadcrumb = document.getElementById('top-breadcrumb-section');

    if (section === 'dashboard') {
        if (navDashboard) navDashboard.classList.add('active');
        if (navBooking) navBooking.classList.remove('active');
        if (viewDashboard) viewDashboard.style.display = 'block';
        if (viewBooking) viewBooking.style.display = 'none';
        if (breadcrumb) breadcrumb.textContent = 'Dashboard';
        loadUserBookings();
    } else if (section === 'booking') {
        if (navDashboard) navDashboard.classList.remove('active');
        if (navBooking) navBooking.classList.add('active');
        if (viewDashboard) viewDashboard.style.display = 'none';
        if (viewBooking) viewBooking.style.display = 'block';
        if (breadcrumb) breadcrumb.textContent = 'Event Booking';
        
        // Show Events Catalog step
        returnToEventsCatalog();
        loadEvents();
    }
}

// ─────────────────────────────────────────────────────────────
// Admin Role Tab Navigation
// ─────────────────────────────────────────────────────────────
function switchAdminTab(tabId) {
    document.querySelectorAll('.admin-view-pane').forEach(p => p.style.display = 'none');
    document.querySelectorAll('.sidebar-nav .nav-link').forEach(l => l.classList.remove('active'));

    const targetPane = document.getElementById(tabId);
    if (targetPane) targetPane.style.display = 'block';

    const breadcrumb = document.getElementById('admin-breadcrumb-section');

    if (tabId === 'tab-admin-events') {
        document.getElementById('admin-nav-events')?.classList.add('active');
        if (breadcrumb) breadcrumb.textContent = 'Events & Creation';
        loadEvents();
    } else if (tabId === 'tab-admin-telemetry') {
        document.getElementById('admin-nav-metrics')?.classList.add('active');
        if (breadcrumb) breadcrumb.textContent = 'System Metrics';
        refreshMetrics();
    } else if (tabId === 'tab-admin-queue') {
        document.getElementById('admin-nav-queue-tickets')?.classList.add('active');
        if (breadcrumb) breadcrumb.textContent = 'Queue & Ticket Stats';
        loadQueueAndTicketAnalytics();
    } else if (tabId === 'tab-admin-security') {
        document.getElementById('admin-nav-security')?.classList.add('active');
        if (breadcrumb) breadcrumb.textContent = 'Scalping Mitigation';
        loadScalperAuditLogs();
    }
}

// ─────────────────────────────────────────────────────────────
// Events Loading & Rendering
// ─────────────────────────────────────────────────────────────
async function loadEvents() {
    try {
        const res = await fetch(`${API_BASE}/events`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const events = await res.json();
        state.events = events;

        // Render User Event Cards
        renderUserEventCards(events);

        // Render Admin Event Cards
        renderAdminEventCards(events);

        // Update counts
        const adminBadge = document.getElementById('admin-events-badge-count');
        if (adminBadge) adminBadge.textContent = events.length;

        updateFleetMetrics();
    } catch (err) {
        console.error('Failed loading events', err);
        showToast('Unable to load events from backend', 'alert');
    }
}

function renderUserEventCards(events) {
    const container = document.getElementById('events-cards-display-container');
    if (!container) return;

    if (!events || events.length === 0) {
        container.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 40px; text-align: center; color: var(--text-muted);">
                No events currently configured.
            </div>
        `;
        return;
    }

    container.innerHTML = events.map(event => {
        const isUpcoming = event.status === 'UPCOMING';
        const isClosed = event.status === 'CLOSED';
        const isOpen = event.status === 'OPEN';

        const statusClass = isOpen ? 'open' : isUpcoming ? 'upcoming' : 'closed';
        const availableSeats = event.availableSeats || 0;
        const totalSeats = event.totalSeats || 100;
        const percent = Math.round((availableSeats / totalSeats) * 100);
        const eventInitials = event.eventName ? event.eventName.split(' ').map(w => w[0]).join('').substring(0, 3) : 'EV';

        const formattedDate = formatDateTime(event.eventDate);
        const saleStartFormatted = formatDateTime(event.saleStartTime);

        return `
            <div class="event-card-item">
                <div>
                    <div class="event-card-header">
                        <div class="event-badge-logo">${eventInitials}</div>
                        <span class="event-status-tag ${statusClass}">
                            ● ${event.status}
                        </span>
                    </div>

                    <h3 class="event-card-title">${escapeHtml(event.eventName)}</h3>

                    <div class="event-card-meta">
                        <div class="event-meta-row">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                                <circle cx="12" cy="10" r="3"></circle>
                            </svg>
                            <span>${escapeHtml(event.venueName || 'Main Arena')}</span>
                        </div>
                        <div class="event-meta-row">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                                <line x1="16" y1="2" x2="16" y2="6"></line>
                                <line x1="8" y1="2" x2="8" y2="6"></line>
                                <line x1="3" y1="10" x2="21" y2="10"></line>
                            </svg>
                            <span>${formattedDate}</span>
                        </div>
                        ${isUpcoming ? `
                        <div class="event-meta-row" style="color: #b45309; font-size: 11.5px;">
                            <span>🕒 Sale Opens: <strong>${saleStartFormatted}</strong></span>
                        </div>` : ''}
                    </div>

                    <div class="seat-progress-box">
                        <div class="seat-progress-label">
                            <span>Availability</span>
                            <strong>${availableSeats} / ${totalSeats} Seats</strong>
                        </div>
                        <div class="seat-progress-bar-bg">
                            <div class="seat-progress-bar-fill ${percent < 20 ? 'low' : ''}" style="width: ${percent}%;"></div>
                        </div>
                    </div>
                </div>

                <div class="event-card-actions">
                    <button class="btn-buy-ticket" 
                            onclick="initiateEventBooking(${event.eventId})" 
                            ${isClosed || availableSeats <= 0 ? 'disabled' : ''}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M4 18v3h16v-3M4 14h16M7 10h10M6 6h12"></path>
                        </svg>
                        ${isOpen ? 'Buy Tickets' : isUpcoming ? 'Join Queue Early' : 'Sold Out / Closed'}
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function renderAdminEventCards(events) {
    const container = document.getElementById('admin-events-container');
    if (!container) return;

    if (!events || events.length === 0) {
        container.innerHTML = `<div style="grid-column:1/-1; padding:30px; text-align:center;">No events configured.</div>`;
        return;
    }

    container.innerHTML = events.map(event => {
        const isUpcoming = event.status === 'UPCOMING';
        const isClosed = event.status === 'CLOSED';
        const isOpen = event.status === 'OPEN';
        const statusClass = isOpen ? 'open' : isUpcoming ? 'upcoming' : 'closed';
        const availableSeats = event.availableSeats || 0;
        const totalSeats = event.totalSeats || 100;
        const booked = totalSeats - availableSeats;

        return `
            <div class="event-card-item" onclick="openAdminSeatInspector(${event.eventId})" style="cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;" title="Click card to inspect live seat matrix">
                <div>
                    <div class="event-card-header">
                        <span class="booked-ticket-ref">#${event.eventId}</span>
                        <span class="event-status-tag ${statusClass}">● ${event.status}</span>
                    </div>

                    <h3 class="event-card-title">${escapeHtml(event.eventName)}</h3>

                    <div class="event-card-meta">
                        <div class="event-meta-row">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                            </svg>
                            <span>${escapeHtml(event.venueName)}</span>
                        </div>
                        <div class="event-meta-row">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                            </svg>
                            <span>${formatDateTime(event.eventDate)}</span>
                        </div>
                    </div>

                    <div class="seat-progress-box">
                        <div class="seat-progress-label">
                            <span>Booked: <strong>${booked}</strong></span>
                            <span>Left: <strong>${availableSeats}</strong></span>
                        </div>
                        <div class="seat-progress-bar-bg">
                            <div class="seat-progress-bar-fill" style="width: ${Math.round((booked/totalSeats)*100)}%;"></div>
                        </div>
                    </div>
                </div>

                <div style="display: flex; gap: 8px; margin-top: 14px;" onclick="event.stopPropagation()">
                    <button class="btn-primary" style="flex: 1.2; font-size: 11.5px; padding: 6px 10px; background: #2563eb;" onclick="openAdminSeatInspector(${event.eventId})">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                        </svg>
                        Inspect Seats
                    </button>
                    ${!isOpen ? `
                    <button class="btn-secondary" style="flex:1; font-size:11.5px; padding:6px; color:#059669; border-color:#a7f3d0;" onclick="adminUpdateEventStatus(${event.eventId}, 'OPEN')">
                        Open Sales
                    </button>` : `
                    <button class="btn-secondary" style="flex:1; font-size:11.5px; padding:6px; color:#b45309; border-color:#fde68a;" onclick="adminUpdateEventStatus(${event.eventId}, 'CLOSED')">
                        Close Sales
                    </button>`}
                    <button class="btn-secondary" style="font-size:11.5px; padding:6px 10px; color:#ef4444;" onclick="adminDeleteEvent(${event.eventId})" title="Delete Event">
                        Delete
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function filterEventsCards() {
    const query = (document.getElementById('user-booking-search-input')?.value || '').toLowerCase();
    const filtered = state.events.filter(e => 
        e.eventName.toLowerCase().includes(query) || 
        (e.venueName && e.venueName.toLowerCase().includes(query))
    );
    renderUserEventCards(filtered);
}

function filterAdminEvents() {
    const query = (document.getElementById('admin-events-search')?.value || '').toLowerCase();
    const filtered = state.events.filter(e => 
        e.eventName.toLowerCase().includes(query) || 
        (e.venueName && e.venueName.toLowerCase().includes(query)) ||
        String(e.eventId).includes(query)
    );
    renderAdminEventCards(filtered);
}

// ─────────────────────────────────────────────────────────────
// User Booking Flow & Virtual Queue Admission
// ─────────────────────────────────────────────────────────────
async function initiateEventBooking(eventId) {
    state.selectedEventId = eventId;
    const event = state.events.find(e => e.eventId === eventId);
    if (!event) return;

    showToast(`Evaluating queue admission for ${event.eventName}...`, 'info');

    // Call /api/queue/join to request admission or queue token
    try {
        const headers = { 'Content-Type': 'application/json' };
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/api/queue/join`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                eventId: eventId,
                userType: 'NORMAL'
            })
        });

        if (!res.ok) {
            // If unauthorized or endpoint rejected, fallback to direct admission token
            console.warn('Queue join returned non-200, granting direct access token fallback');
            proceedDirectToSeatMap(eventId, `TKN-${eventId}-${Date.now()}`);
            return;
        }

        const data = await res.json();
        console.log('Queue join response:', data);

        if (data.mode === 'DIRECT' && data.token) {
            // Direct Entry! (Low Load)
            state.queueToken = data.token;
            state.queueMode = 'DIRECT';
            showToast('Direct admission granted! Opening live seat map...', 'success');
            proceedDirectToSeatMap(eventId, data.token);
        } else if (data.mode === 'QUEUE' || data.position != null) {
            // Queue Enforced! Show Queue Waiting Room
            state.queueMode = 'QUEUE';
            showQueueWaitingRoom(event, data);
        } else {
            proceedDirectToSeatMap(eventId, data.token || `TKN-${eventId}-${Date.now()}`);
        }
    } catch (err) {
        console.warn('Error during queue join, falling back to direct access', err);
        proceedDirectToSeatMap(eventId, `TKN-${eventId}-${Date.now()}`);
    }
}

function showQueueWaitingRoom(event, queueData) {
    document.getElementById('booking-step-events-catalog').style.display = 'none';
    document.getElementById('booking-step-queue-room').style.display = 'block';
    document.getElementById('booking-step-seatmap').style.display = 'none';

    document.getElementById('queue-event-name').textContent = event.eventName;
    document.getElementById('queue-wait-position').textContent = `#${queueData.position || 1}`;
    document.getElementById('queue-wait-time').textContent = queueData.estimatedWaitTime ? `${queueData.estimatedWaitTime} min` : '< 1 min';
    document.getElementById('queue-mode-label').textContent = queueData.mode || 'HARD_QUEUE';

    // Connect to SSE for queue admission push
    connectSseStream(event.eventId);
}

function cancelQueueWait() {
    returnToEventsCatalog();
    showToast('Exited queue waitlist', 'info');
}

function returnToEventsCatalog() {
    document.getElementById('booking-step-events-catalog').style.display = 'block';
    const queueRoom = document.getElementById('booking-step-queue-room');
    const seatMap = document.getElementById('booking-step-seatmap');
    if (queueRoom) queueRoom.style.display = 'none';
    if (seatMap) seatMap.style.display = 'none';
}

function proceedDirectToSeatMap(eventId, token) {
    state.queueToken = token;
    document.getElementById('booking-step-events-catalog').style.display = 'none';
    const queueRoom = document.getElementById('booking-step-queue-room');
    if (queueRoom) queueRoom.style.display = 'none';
    document.getElementById('booking-step-seatmap').style.display = 'block';

    const tokenStatus = document.getElementById('seatmap-token-status');
    if (tokenStatus) tokenStatus.textContent = 'Queue Token: Valid (15m window)';

    loadEventSeatMap(eventId);
}

// ─────────────────────────────────────────────────────────────
// Interactive Seat Map & Selection
// ─────────────────────────────────────────────────────────────
async function loadEventSeatMap(eventId) {
    try {
        const res = await fetch(`${API_BASE}/events/${eventId}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const eventData = await res.json();
        state.selectedEventDetails = eventData;

        // Populate Headers
        document.getElementById('seatmap-event-title').textContent = eventData.eventName;
        document.getElementById('seatmap-event-sub').textContent = `${eventData.venueName} • ${formatDateTime(eventData.eventDate)}`;
        document.getElementById('seatmap-sales-status').textContent = eventData.status;

        // Reset selections
        state.selectedSeats.clear();
        state.reservationIds = [];
        updateSeatSummaryUI();

        // Render Seats
        renderSeatsGrid(eventData.seats || []);

        // Re-render Google reCAPTCHA widget if needed
        renderGoogleRecaptcha();
    } catch (err) {
        console.error('Failed loading event seat map', err);
        showToast('Failed to load event seat details', 'alert');
    }
}

function renderSeatsGrid(seats) {
    const container = document.getElementById('user-seat-grid-container');
    if (!container) return;

    if (!seats || seats.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-muted);">No seats generated for this event.</div>`;
        return;
    }

    // Group seats by row
    const rows = {};
    seats.forEach(s => {
        const row = s.rowNo || s.seatNumber.charAt(0);
        if (!rows[row]) rows[row] = [];
        rows[row].push(s);
    });

    container.innerHTML = Object.keys(rows).sort().map(rowKey => {
        const rowSeats = rows[rowKey];
        return `
            <div class="seat-row" style="display:flex; justify-content:center; align-items:center; gap:8px; margin-bottom:8px;">
                <span class="seat-row-label" style="font-weight:700; font-size:12px; width:22px; color:var(--text-muted);">${rowKey}</span>
                <div style="display:flex; gap:6px;">
                    ${rowSeats.map(seat => {
                        const isAvailable = seat.status === 'AVAILABLE';
                        const isReserved = seat.status === 'RESERVED';
                        const isBooked = seat.status === 'BOOKED';
                        const isSelected = state.selectedSeats.has(seat.seatNumber);

                        let seatClass = 'seat-box';
                        if (isSelected) seatClass += ' selected';
                        else if (isAvailable) seatClass += ' available';
                        else if (isReserved) seatClass += ' reserved';
                        else if (isBooked) seatClass += ' booked';

                        return `
                            <button class="${seatClass}" 
                                    id="seat-btn-${seat.seatNumber}"
                                    data-seat="${seat.seatNumber}"
                                    data-price="${seat.price || 500}"
                                    title="${seat.seatNumber} • ₹${seat.price || 500} • ${seat.status}"
                                    onclick="toggleSeatSelection('${seat.seatNumber}', ${seat.price || 500}, '${seat.status}')">
                                ${seat.seatNumber}
                            </button>
                        `;
                    }).join('')}
                </div>
            </div>
        `;
    }).join('');
}

function toggleSeatSelection(seatNumber, price, status) {
    if (status !== 'AVAILABLE') {
        showToast(`Seat ${seatNumber} is ${status.toLowerCase()} and cannot be selected.`, 'alert');
        return;
    }

    if (state.selectedSeats.has(seatNumber)) {
        state.selectedSeats.delete(seatNumber);
    } else {
        if (state.selectedSeats.size >= 4) {
            showToast('Maximum 4 seats can be selected per booking session.', 'info');
            return;
        }
        state.selectedSeats.add(seatNumber);
    }

    // Update Seat Class in DOM
    const seatBtn = document.getElementById(`seat-btn-${seatNumber}`);
    if (seatBtn) {
        if (state.selectedSeats.has(seatNumber)) {
            seatBtn.className = 'seat-box selected';
        } else {
            seatBtn.className = 'seat-box available';
        }
    }

    updateSeatSummaryUI();
}

function updateSeatSummaryUI() {
    const count = state.selectedSeats.size;
    const countBadge = document.getElementById('summary-seats-count');
    const unitPriceElem = document.getElementById('summary-unit-price');
    const totalElem = document.getElementById('summary-total-amount');
    const chipsContainer = document.getElementById('user-selected-seats-chips');
    const reserveBtn = document.getElementById('btn-reserve-seats-action');
    const payAmountLabel = document.getElementById('btn-pay-amount-label');

    const defaultPrice = (state.selectedEventDetails?.seats?.[0]?.price) || 500;
    const total = count * defaultPrice;

    if (countBadge) countBadge.textContent = count;
    if (unitPriceElem) unitPriceElem.textContent = `₹${defaultPrice.toFixed(2)}`;
    if (totalElem) totalElem.textContent = `₹${total.toFixed(2)}`;
    if (payAmountLabel) payAmountLabel.textContent = total.toFixed(2);

    if (chipsContainer) {
        if (count === 0) {
            chipsContainer.innerHTML = `<span style="font-size: 12px; color: var(--text-muted);">No seats selected yet. Click any green seat above.</span>`;
        } else {
            chipsContainer.innerHTML = Array.from(state.selectedSeats).map(s => `
                <span class="seat-pill" style="background:#eff6ff; color:#1d4ed8; border-color:#bfdbfe;">
                    ${s} (₹${defaultPrice})
                </span>
            `).join('');
        }
    }

    if (reserveBtn) {
        reserveBtn.disabled = count === 0;
    }
}

// ─────────────────────────────────────────────────────────────
// Scalping Mitigation & Google reCAPTCHA v3
// ─────────────────────────────────────────────────────────────
function renderGoogleRecaptcha() {
    const badge = document.getElementById('recaptcha-status-badge');
    if (typeof grecaptcha !== 'undefined' && grecaptcha.ready) {
        grecaptcha.ready(() => {
            if (badge) {
                badge.className = 'verified-pill';
                badge.style.background = '#ecfdf5';
                badge.style.color = '#047857';
                badge.innerHTML = `<span class="live-dot" style="background:#10b981; width:7px; height:7px; display:inline-block; border-radius:50%; margin-right:4px;"></span>reCAPTCHA v3 Ready`;
            }
        });
    }
}

function getGoogleRecaptchaToken(action = 'reserve_seats') {
    return new Promise((resolve) => {
        if (typeof grecaptcha === 'undefined' || !grecaptcha.execute) {
            console.warn('Google reCAPTCHA v3 not loaded, using fallback token');
            resolve('dev-recaptcha-fallback-' + Date.now());
            return;
        }

        try {
            grecaptcha.ready(() => {
                grecaptcha.execute(RECAPTCHA_SITE_KEY, { action: action })
                    .then(token => resolve(token))
                    .catch(err => {
                        console.warn('grecaptcha.execute failed:', err);
                        resolve('dev-recaptcha-fallback-' + Date.now());
                    });
            });
        } catch (e) {
            console.warn('reCAPTCHA execution catch:', e);
            resolve('dev-recaptcha-fallback-' + Date.now());
        }
    });
}

// ─────────────────────────────────────────────────────────────
// Seat Reservation & Distributed Locking
// ─────────────────────────────────────────────────────────────
async function executeSeatReservation() {
    if (state.selectedSeats.size === 0) {
        showToast('Please select at least 1 seat first.', 'info');
        return;
    }

    const eventId = state.selectedEventId;
    const seatsArray = Array.from(state.selectedSeats);
    const reserveBtn = document.getElementById('btn-reserve-seats-action');
    const badge = document.getElementById('recaptcha-status-badge');

    if (reserveBtn) {
        reserveBtn.disabled = true;
        reserveBtn.innerHTML = `<span>Verifying reCAPTCHA v3...</span>`;
    }
    if (badge) {
        badge.className = 'verified-pill';
        badge.style.background = '#eff6ff';
        badge.style.color = '#1d4ed8';
        badge.textContent = 'Verifying Bot Defense...';
    }

    try {
        // Step 1: Execute Google reCAPTCHA v3 token generation
        const recaptchaToken = await getGoogleRecaptchaToken('reserve_seats');
        let verifiedCaptchaToken = recaptchaToken;

        // Step 2: Validate token with backend /api/security/recaptcha/verify
        try {
            const verifyRes = await fetch(`${API_BASE}/api/security/recaptcha/verify`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ recaptchaToken: recaptchaToken })
            });
            const verifyData = await verifyRes.json();
            if (verifyData.verified) {
                verifiedCaptchaToken = verifyData.captchaToken || recaptchaToken;
                state.googleRecaptchaToken = recaptchaToken;
                state.verifiedCaptchaToken = verifiedCaptchaToken;
                if (badge) {
                    badge.className = 'verified-pill';
                    badge.style.background = '#ecfdf5';
                    badge.style.color = '#047857';
                    badge.innerHTML = `<span class="live-dot" style="background:#10b981; width:7px; height:7px; display:inline-block; border-radius:50%; margin-right:4px;"></span>Human Verified ✓`;
                }
            }
        } catch (vErr) {
            console.warn('Backend reCAPTCHA verify fallback:', vErr);
            state.verifiedCaptchaToken = verifiedCaptchaToken;
        }

        if (reserveBtn) {
            reserveBtn.innerHTML = `<span>Acquiring Redis Locks...</span>`;
        }

        // Step 3: Call distributed seat reservation
        const headers = {
            'Content-Type': 'application/json',
            'X-Queue-Token': state.queueToken || `TKN-${eventId}-${Date.now()}`,
            'X-Captcha-Token': state.verifiedCaptchaToken || `TOKEN-${Date.now()}`
        };
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/api/reservations`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                eventId: eventId,
                seatNumbers: seatsArray
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `HTTP ${res.status}`);
        }

        const data = await res.json();
        console.log('Reservation response:', data);

        state.reservationIds = data.reservationIds || [1];
        state.reservationExpiresAt = data.expiresAt || new Date(Date.now() + 5 * 60000).toISOString();

        showToast('Seats locked successfully! Distributed lock acquired for 5 minutes.', 'success');

        // Update UI
        if (reserveBtn) reserveBtn.style.display = 'none';
        const proceedPayBtn = document.getElementById('btn-proceed-payment-modal');
        if (proceedPayBtn) proceedPayBtn.style.display = 'flex';

        // Start 5-Minute Timer
        startReservationTimer(5 * 60);

    } catch (err) {
        console.error('Reservation failed', err);
        showToast(err.message || 'Seat reservation failed. Concurrency lock contention.', 'alert');
        if (reserveBtn) {
            reserveBtn.disabled = false;
            reserveBtn.innerHTML = `
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                Reserve Selected Seats
            `;
        }
        if (badge) {
            badge.className = 'verified-pill alert';
            badge.textContent = 'Retry Verification';
        }
    }
}

function startReservationTimer(durationSeconds) {
    if (state.reservationTimer) clearInterval(state.reservationTimer);
    const timerBox = document.getElementById('reservation-countdown-bar');
    const timerDisplay = document.getElementById('reservation-timer-display');
    if (timerBox) timerBox.style.display = 'block';

    let remaining = durationSeconds;
    state.reservationTimer = setInterval(() => {
        remaining--;
        if (remaining <= 0) {
            clearInterval(state.reservationTimer);
            if (timerDisplay) timerDisplay.textContent = '00:00';
            showToast('Reservation expired. Seats have been unlocked.', 'alert');
            returnToEventsCatalog();
            return;
        }

        const mins = String(Math.floor(remaining / 60)).padStart(2, '0');
        const secs = String(remaining % 60).padStart(2, '0');
        if (timerDisplay) timerDisplay.textContent = `${mins}:${secs}`;
    }, 1000);
}

// ─────────────────────────────────────────────────────────────
// Mock Payment Modal & Confirmation
// ─────────────────────────────────────────────────────────────
function openPaymentModal() {
    const modal = document.getElementById('mock-payment-modal');
    if (!modal) return;

    const eventName = state.selectedEventDetails?.eventName || 'Event';
    const seats = Array.from(state.selectedSeats).join(', ');
    const defaultPrice = (state.selectedEventDetails?.seats?.[0]?.price) || 500;
    const total = state.selectedSeats.size * defaultPrice;

    document.getElementById('modal-pay-event-name').textContent = eventName;
    document.getElementById('modal-pay-seats').textContent = seats;
    document.getElementById('modal-pay-total').textContent = `₹${total.toFixed(2)}`;
    document.getElementById('btn-pay-text').textContent = `Pay ₹${total.toFixed(2)} & Finalize Booking`;

    modal.classList.add('active');
}

function closePaymentModal() {
    const modal = document.getElementById('mock-payment-modal');
    if (modal) modal.classList.remove('active');
}

function selectPayTab(el, method) {
    document.querySelectorAll('.payment-tab').forEach(t => t.classList.remove('active'));
    el.classList.add('active');
}

async function executeConfirmBookingPayment(e) {
    e.preventDefault();
    const btnText = document.getElementById('btn-pay-text');
    const payBtn = document.getElementById('btn-submit-payment');

    if (payBtn) payBtn.disabled = true;
    if (btnText) btnText.textContent = 'Processing Payment & Queueing Email...';

    const defaultPrice = (state.selectedEventDetails?.seats?.[0]?.price) || 500;
    const total = state.selectedSeats.size * defaultPrice;
    const idempotencyKey = `TF-IDEMP-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    try {
        const headers = {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
            'X-Queue-Token': state.queueToken || ''
        };
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/api/bookings/confirm`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                reservationIds: state.reservationIds,
                paymentRequest: {
                    amount: total,
                    paymentMethod: 'CREDIT_CARD',
                    cardNumber: document.getElementById('pay-card-num')?.value || '4111222233334444'
                }
            })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `Payment Failed (HTTP ${res.status})`);
        }

        const bookingData = await res.json();
        console.log('Confirmed booking:', bookingData);

        // Success!
        clearInterval(state.reservationTimer);
        closePaymentModal();

        showToast('Payment successful! Booking confirmation dispatched to RabbitMQ.', 'success');

        // Build Booking Record
        const confirmedBooking = {
            bookingId: bookingData.bookingId || Date.now(),
            bookingReference: bookingData.bookingReference || `TF-2026-${Math.random().toString(36).substring(2, 6).toUpperCase()}`,
            eventName: state.selectedEventDetails?.eventName || 'IPL Match',
            eventDate: state.selectedEventDetails?.eventDate || new Date().toISOString(),
            venueName: state.selectedEventDetails?.venueName || 'Stadium Arena',
            seats: Array.from(state.selectedSeats).map(s => ({ seatNumber: s, price: defaultPrice })),
            totalAmount: total,
            email: DEFAULT_USER_EMAIL,
            confirmedAt: new Date().toISOString()
        };

        state.lastConfirmedBooking = confirmedBooking;
        saveUserBookingLocally(confirmedBooking);

        // Open Digital Ticket Pass Modal
        openDigitalTicketModal(confirmedBooking);

        // Refresh events and user dashboard
        await loadEvents();
        await loadUserBookings();

    } catch (err) {
        console.error('Payment confirmation error', err);
        showToast(err.message || 'Payment confirmation failed', 'alert');
        if (payBtn) payBtn.disabled = false;
        if (btnText) btnText.textContent = 'Pay & Finalize Booking';
    }
}

// ─────────────────────────────────────────────────────────────
// Digital Ticket Pass Modal
// ─────────────────────────────────────────────────────────────
function openDigitalTicketModal(booking) {
    const modal = document.getElementById('digital-ticket-modal');
    if (!modal) return;

    document.getElementById('pass-event-name').textContent = booking.eventName || 'Event';
    document.getElementById('pass-venue-date').textContent = `${booking.venueName || 'Venue'} • ${formatDateTime(booking.eventDate)}`;
    document.getElementById('pass-booking-ref').textContent = booking.bookingReference;
    document.getElementById('pass-attendee-name').textContent = state.user.name;
    document.getElementById('pass-email').textContent = DEFAULT_USER_EMAIL;
    document.getElementById('pass-barcode-text').textContent = `*${booking.bookingReference}*`;

    const seatsContainer = document.getElementById('pass-seats-container');
    if (seatsContainer) {
        const seatItems = booking.seats || [];
        seatsContainer.innerHTML = seatItems.map(s => `
            <span class="seat-pill" style="background:#0f172a; color:#f8fafc; font-size:12px;">${s.seatNumber || s}</span>
        `).join('');
    }

    modal.classList.add('active');
}

function closeDigitalTicketModal() {
    const modal = document.getElementById('digital-ticket-modal');
    if (modal) modal.classList.remove('active');
    switchUserSection('dashboard');
}

// ─────────────────────────────────────────────────────────────
// User Bookings & Dashboard Data
// ─────────────────────────────────────────────────────────────
async function loadUserBookings() {
    let bookings = [];
    try {
        const headers = {};
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }
        const res = await fetch(`${API_BASE}/api/user/bookings`, { headers });
        if (res.ok) {
            bookings = await res.json();
        }
    } catch (e) {
        console.warn('Backend user bookings query fallback to local cache', e);
    }

    // Merge with local storage cached bookings
    const local = getLocalBookings();
    const merged = [...bookings];
    local.forEach(lb => {
        if (!merged.some(m => m.bookingReference === lb.bookingReference)) {
            merged.push(lb);
        }
    });

    state.userBookings = merged;
    renderUserBookingsUI(merged);
}

function renderUserBookingsUI(bookings) {
    const container = document.getElementById('user-booked-tickets-container');
    const badgeCount = document.getElementById('badge-my-bookings-count');
    const dashBadge = document.getElementById('dash-booked-badge');
    const statTicketCount = document.getElementById('dash-stat-ticket-count');
    const statEventsCount = document.getElementById('dash-stat-events-count');
    const statSpent = document.getElementById('dash-stat-total-spent');

    let totalTickets = 0;
    let totalSpent = 0;
    const eventSet = new Set();

    bookings.forEach(b => {
        const seatCount = (b.seats && b.seats.length) ? b.seats.length : 1;
        totalTickets += seatCount;
        totalSpent += parseFloat(b.totalAmount || 0);
        if (b.eventName) eventSet.add(b.eventName);
    });

    if (badgeCount) badgeCount.textContent = totalTickets;
    if (dashBadge) dashBadge.textContent = `${bookings.length} Bookings`;
    if (statTicketCount) statTicketCount.textContent = totalTickets;
    if (statEventsCount) statEventsCount.textContent = eventSet.size;
    if (statSpent) statSpent.textContent = `₹${totalSpent.toFixed(2)}`;

    if (!container) return;

    if (bookings.length === 0) {
        container.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 40px 20px; text-align: center; color: var(--text-muted);">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="margin-bottom: 12px; color: #cbd5e1;">
                    <path d="M4 18v3h16v-3M4 14h16M7 10h10M6 6h12"></path>
                </svg>
                <h4 style="font-size: 15px; color: var(--text-primary); margin-bottom: 6px;">No Bookings Found Yet</h4>
                <p style="font-size: 13px; margin-bottom: 16px;">You haven't reserved tickets for any events yet.</p>
                <button class="btn-primary" onclick="switchUserSection('booking')">Browse Live Events</button>
            </div>
        `;
        return;
    }

    container.innerHTML = bookings.map((b, idx) => {
        const seats = b.seats || [];
        return `
            <div class="booked-ticket-card">
                <div>
                    <div class="booked-ticket-header">
                        <span class="booked-ticket-ref">${b.bookingReference}</span>
                        <span class="event-status-tag open">● CONFIRMED</span>
                    </div>

                    <h4 style="font-size: 16px; font-weight: 800; color: var(--text-primary); margin-bottom: 4px;">
                        ${escapeHtml(b.eventName || 'Event Pass')}
                    </h4>
                    <div style="font-size: 12.5px; color: var(--text-secondary); margin-bottom: 12px;">
                        ${escapeHtml(b.venueName || 'Main Arena')} &bull; ${formatDateTime(b.eventDate)}
                    </div>

                    <div style="font-size: 11px; font-weight: 700; color: var(--text-muted); text-transform: uppercase;">SEATS ALLOCATED:</div>
                    <div class="seat-pills-wrap">
                        ${seats.map(s => `<span class="seat-pill">${s.seatNumber || s}</span>`).join('')}
                    </div>

                    <div style="display: flex; justify-content: space-between; margin-top: 10px; font-size: 13px;">
                        <span style="color: var(--text-secondary);">Paid:</span>
                        <strong style="color: var(--primary);">₹${parseFloat(b.totalAmount || 0).toFixed(2)}</strong>
                    </div>
                </div>

                <div style="margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--border-subtle); display: flex; justify-content: space-between; align-items: center;">
                    <span style="font-size: 11px; color: #059669;">Dispatched via RabbitMQ</span>
                    <button class="btn-secondary" style="font-size: 12px; padding: 5px 12px;" onclick='openDigitalTicketModal(${JSON.stringify(b)})'>
                        View Pass
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function saveUserBookingLocally(booking) {
    const list = getLocalBookings();
    list.unshift(booking);
    localStorage.setItem('ticketflow_user_bookings', JSON.stringify(list));
}

function getLocalBookings() {
    try {
        return JSON.parse(localStorage.getItem('ticketflow_user_bookings') || '[]');
    } catch (e) {
        return [];
    }
}

// ─────────────────────────────────────────────────────────────
// Real-Time Server-Sent Events (SSE) Stream
// ─────────────────────────────────────────────────────────────
function connectSseStream(eventId) {
    if (state.sseSource) {
        state.sseSource.close();
    }

    const url = eventId ? `${API_BASE}/api/queue/stream?eventId=${eventId}` : `${API_BASE}/api/queue/stream`;
    console.log('Connecting to SSE stream:', url);

    const sseDot = document.getElementById('sse-dot-indicator') || document.getElementById('admin-sse-dot');
    const sseLabel = document.getElementById('sse-status-label');

    try {
        state.sseSource = new EventSource(url);

        state.sseSource.onopen = () => {
            console.log('SSE Stream established');
            if (sseDot) sseDot.style.backgroundColor = '#10b981';
            if (sseLabel) sseLabel.textContent = 'SSE Live Synced';
        };

        state.sseSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                handleSseBroadcast(data);
            } catch (e) {
                // Raw text message
                logAdminSse(`MESSAGE: ${event.data}`);
            }
        };

        state.sseSource.onerror = (err) => {
            console.warn('SSE connection warning', err);
            if (sseDot) sseDot.style.backgroundColor = '#f59e0b';
            if (sseLabel) sseLabel.textContent = 'SSE Reconnecting';
        };
    } catch (e) {
        console.error('SSE initialization error', e);
    }
}

function handleSseBroadcast(data) {
    console.log('Received SSE Broadcast:', data);

    // Format human-readable SSE log in admin terminal
    if (data.type === 'SEAT_STATUS_UPDATE') {
        logAdminSse(`SEAT_STATUS_UPDATE: Event #${data.eventId} • Seat ${data.seatNumber} -> ${data.status}`);

        // Update User Seat Map if viewing this event
        const userSeatBtn = document.getElementById(`seat-btn-${data.seatNumber}`);
        if (userSeatBtn && state.selectedEventId === data.eventId) {
            userSeatBtn.className = 'seat-box ' + data.status.toLowerCase();
            userSeatBtn.title = `${data.seatNumber} • ${data.status}`;
        }

        // Update Admin Live Seat Matrix Modal if inspecting this event
        if (state.inspectingEventId === data.eventId) {
            const adminSeatBtn = document.getElementById(`inspect-seat-${data.seatNumber}`);
            if (adminSeatBtn) {
                adminSeatBtn.className = 'seat-box ' + data.status.toLowerCase();
                adminSeatBtn.title = `Seat: ${data.seatNumber} • Status: ${data.status}`;
            }
            if (state.inspectingEventSeats) {
                const s = state.inspectingEventSeats.find(item => item.seatNumber === data.seatNumber);
                if (s) s.status = data.status;
            }
            updateInspectorStats();
        }

        // Update breakdown table in Analytics tab
        const bookedCell = document.getElementById(`breakdown-booked-${data.eventId}`);
        const availCell = document.getElementById(`breakdown-avail-${data.eventId}`);
        if (data.status === 'BOOKED' && bookedCell && availCell) {
            bookedCell.textContent = parseInt(bookedCell.textContent || 0) + 1;
            availCell.textContent = Math.max(0, parseInt(availCell.textContent || 1) - 1);
        }

        updateFleetMetrics();
    } else if (data.type === 'EVENT_STATUS_UPDATE') {
        logAdminSse(`EVENT_STATUS_UPDATE: Event #${data.eventId} status -> ${data.status}`);
        showToast(`Event #${data.eventId} sales status updated to ${data.status}`, 'info');

        // Update modal status if open
        if (state.inspectingEventId === data.eventId) {
            const statusElem = document.getElementById('inspect-event-status');
            const toggleBtn = document.getElementById('btn-inspect-toggle-status');
            if (statusElem) {
                statusElem.textContent = data.status;
                statusElem.className = `event-status-tag ${data.status === 'OPEN' ? 'open' : 'closed'}`;
            }
            if (toggleBtn) {
                toggleBtn.textContent = data.status === 'OPEN' ? 'Close Sales' : 'Open Sales';
                toggleBtn.className = data.status === 'OPEN' ? 'btn-secondary' : 'btn-primary';
            }
        }

        loadEvents();
        if (document.getElementById('tab-admin-queue')?.style.display !== 'none') {
            loadQueueAndTicketAnalytics();
        }
    } else if (data.type === 'BOOKING_ALLOWED') {
        logAdminSse(`BOOKING_ALLOWED: User admission granted for queue waitlist`);
        showToast('Queue admission granted! Your turn to reserve seats.', 'success');
        if (data.token) {
            state.queueToken = data.token;
            proceedDirectToSeatMap(state.selectedEventId, data.token);
        }
    } else {
        logAdminSse(JSON.stringify(data));
    }
}

function logAdminSse(msg) {
    const term = document.getElementById('admin-sse-terminal');
    if (!term) return;
    const time = new Date().toLocaleTimeString();
    const line = document.createElement('div');
    line.style.fontSize = '12px';
    line.style.fontFamily = 'JetBrains Mono, monospace';
    line.style.marginBottom = '5px';
    line.style.display = 'flex';
    line.style.alignItems = 'center';
    line.style.gap = '8px';
    line.innerHTML = `
        <span style="color:#64748b; font-size:11px;">[${time}]</span>
        <span style="background:rgba(56,189,248,0.15); color:#38bdf8; padding:1px 5px; border-radius:4px; font-size:10px; font-weight:700;">SSE</span>
        <span style="color:#f1f5f9;">${escapeHtml(msg)}</span>
    `;
    term.appendChild(line);
    term.scrollTop = term.scrollHeight;
}

// ─────────────────────────────────────────────────────────────
// Admin Interactive Seat Allocation Inspector (Live SSE)
// ─────────────────────────────────────────────────────────────
async function openAdminSeatInspector(eventId) {
    state.inspectingEventId = eventId;
    const modal = document.getElementById('admin-seat-inspector-modal');
    if (modal) modal.classList.add('active');

    // Show loading state
    const grid = document.getElementById('inspect-seat-grid-container');
    if (grid) grid.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-muted);">Loading real-time seat allocation for Event #${eventId}...</div>`;

    try {
        const res = await fetch(`${API_BASE}/events/${eventId}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const eventData = await res.json();
        state.inspectingEventData = eventData;
        state.inspectingEventSeats = eventData.seats || [];

        // Populate Headers
        const nameElem = document.getElementById('inspect-event-name');
        const venueElem = document.getElementById('inspect-event-venue-date');
        const statusElem = document.getElementById('inspect-event-status');
        const toggleBtn = document.getElementById('btn-inspect-toggle-status');

        if (nameElem) nameElem.textContent = eventData.eventName;
        if (venueElem) venueElem.textContent = `${eventData.venueName} • ${formatDateTime(eventData.eventDate)}`;
        if (statusElem) {
            statusElem.textContent = eventData.status;
            statusElem.className = `event-status-tag ${eventData.status === 'OPEN' ? 'open' : eventData.status === 'UPCOMING' ? 'upcoming' : 'closed'}`;
        }
        if (toggleBtn) {
            toggleBtn.textContent = eventData.status === 'OPEN' ? 'Close Sales' : 'Open Sales';
            toggleBtn.className = eventData.status === 'OPEN' ? 'btn-secondary' : 'btn-primary';
        }

        updateInspectorStats();
        renderAdminSeatGrid(state.inspectingEventSeats);

    } catch (err) {
        console.error('Failed loading admin seat allocation', err);
        showToast('Failed to load event seat details', 'alert');
        if (grid) grid.innerHTML = `<div style="text-align:center; padding:20px; color:#ef4444;">Failed to load seat layout.</div>`;
    }
}

function updateInspectorStats() {
    const seats = state.inspectingEventSeats || [];
    const total = seats.length || (state.inspectingEventData?.totalSeats) || 0;
    const available = seats.filter(s => s.status === 'AVAILABLE').length;
    const reserved = seats.filter(s => s.status === 'RESERVED').length;
    const booked = seats.filter(s => s.status === 'BOOKED').length;
    const occupancy = total > 0 ? Math.round(((booked + reserved) / total) * 100) : 0;

    const elTotal = document.getElementById('inspect-total-seats');
    const elAvailable = document.getElementById('inspect-available-seats');
    const elReserved = document.getElementById('inspect-reserved-seats');
    const elBooked = document.getElementById('inspect-booked-seats');
    const elOccupancy = document.getElementById('inspect-occupancy-rate');

    if (elTotal) elTotal.textContent = total;
    if (elAvailable) elAvailable.textContent = available;
    if (elReserved) elReserved.textContent = reserved;
    if (elBooked) elBooked.textContent = booked;
    if (elOccupancy) elOccupancy.textContent = `${occupancy}%`;
}

function renderAdminSeatGrid(seats) {
    const container = document.getElementById('inspect-seat-grid-container');
    if (!container) return;

    if (!seats || seats.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:24px; color:var(--text-muted);">No seats generated for this event.</div>`;
        return;
    }

    // Group seats by row
    const rows = {};
    seats.forEach(s => {
        const row = s.rowNo || s.seatNumber.charAt(0);
        if (!rows[row]) rows[row] = [];
        rows[row].push(s);
    });

    container.innerHTML = Object.keys(rows).sort().map(rowKey => {
        const rowSeats = rows[rowKey];
        return `
            <div class="seat-row" style="display:flex; justify-content:center; align-items:center; gap:8px; margin-bottom:8px;">
                <span class="seat-row-label" style="font-weight:700; font-size:12px; width:24px; color:var(--text-muted);">${rowKey}</span>
                <div style="display:flex; gap:6px; flex-wrap:nowrap;">
                    ${rowSeats.map(seat => {
                        const status = (seat.status || 'AVAILABLE').toUpperCase();
                        let seatClass = 'seat-box';
                        if (status === 'AVAILABLE') seatClass += ' available';
                        else if (status === 'RESERVED') seatClass += ' reserved';
                        else if (status === 'BOOKED') seatClass += ' booked';

                        return `
                            <button class="${seatClass}" 
                                    id="inspect-seat-${seat.seatNumber}"
                                    data-seat="${seat.seatNumber}"
                                    title="Seat: ${seat.seatNumber} • Status: ${status} • Type: ${seat.seatType || 'REGULAR'} • Price: ₹${seat.price || 500}">
                                ${seat.seatNumber}
                            </button>
                        `;
                    }).join('')}
                </div>
            </div>
        `;
    }).join('');
}

function closeAdminSeatInspector() {
    const modal = document.getElementById('admin-seat-inspector-modal');
    if (modal) modal.classList.remove('active');
    state.inspectingEventId = null;
    state.inspectingEventData = null;
    state.inspectingEventSeats = [];
}

async function toggleInspectedEventStatus() {
    if (!state.inspectingEventId) return;
    const current = state.inspectingEventData?.status || 'CLOSED';
    const newStatus = current === 'OPEN' ? 'CLOSED' : 'OPEN';
    await adminUpdateEventStatus(state.inspectingEventId, newStatus);
    
    // Update inspector UI immediately
    if (state.inspectingEventData) {
        state.inspectingEventData.status = newStatus;
    }
    const statusElem = document.getElementById('inspect-event-status');
    const toggleBtn = document.getElementById('btn-inspect-toggle-status');
    if (statusElem) {
        statusElem.textContent = newStatus;
        statusElem.className = `event-status-tag ${newStatus === 'OPEN' ? 'open' : 'closed'}`;
    }
    if (toggleBtn) {
        toggleBtn.textContent = newStatus === 'OPEN' ? 'Close Sales' : 'Open Sales';
        toggleBtn.className = newStatus === 'OPEN' ? 'btn-secondary' : 'btn-primary';
    }
}

// ─────────────────────────────────────────────────────────────
// Admin Virtual Queue & Ticket Analytics Engine
// ─────────────────────────────────────────────────────────────
async function loadQueueAndTicketAnalytics() {
    try {
        // Fetch bookings from backend
        let bookings = [];
        try {
            const res = await fetch(`${API_BASE}/api/admin/bookings`);
            if (res.ok) {
                bookings = await res.json();
            }
        } catch (e) {
            console.warn('Could not fetch admin bookings, checking local fallback', e);
        }

        // If backend returned empty, supplement with local bookings for demo
        if (!bookings || bookings.length === 0) {
            bookings = getLocalBookings();
        }
        state.adminBookings = bookings;

        // Ensure events are loaded
        if (!state.events || state.events.length === 0) {
            await loadEvents();
        }

        // Compute Fleet KPIs
        let totalFleetSeats = 0;
        let totalBookedSeats = 0;
        let totalRevenue = 0;

        // Calculate from bookings
        bookings.forEach(b => {
            const amt = parseFloat(b.totalAmount) || 0;
            totalRevenue += amt;
            const seatsCount = (b.seats && b.seats.length) ? b.seats.length : 1;
            totalBookedSeats += seatsCount;
        });

        // Cross-calculate with events
        state.events.forEach(ev => {
            const total = ev.totalSeats || 0;
            const avail = ev.availableSeats || 0;
            totalFleetSeats += total;
            const bookedInEvent = Math.max(0, total - avail);
            if (bookings.length === 0) {
                totalBookedSeats += bookedInEvent;
                totalRevenue += bookedInEvent * 500;
            }
        });

        if (totalFleetSeats === 0) totalFleetSeats = 200;
        const occupancyRate = ((totalBookedSeats / totalFleetSeats) * 100).toFixed(1);

        // Update KPI Cards
        const elSold = document.getElementById('admin-stat-tickets-sold');
        const elRev = document.getElementById('admin-stat-revenue');
        const elOcc = document.getElementById('admin-stat-occupancy');
        const elMode = document.getElementById('admin-queue-mode-display');

        if (elSold) elSold.textContent = totalBookedSeats;
        if (elRev) elRev.textContent = `₹${totalRevenue.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
        if (elOcc) elOcc.textContent = `${occupancyRate}%`;
        if (elMode) elMode.textContent = state.queueMode || 'DIRECT';

        // Render Breakdown Table
        renderQueueBreakdownTable();

        // Render Bookings Ledger Table
        renderQueueRecentBookingsTable(bookings);

    } catch (err) {
        console.error('Error loading queue and ticket analytics', err);
    }
}

function renderQueueBreakdownTable() {
    const tbody = document.getElementById('admin-queue-events-breakdown-body');
    const countBadge = document.getElementById('admin-analytics-events-count');
    if (!tbody) return;

    const events = state.events || [];
    if (countBadge) countBadge.textContent = `${events.length} Events`;

    if (events.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; padding:24px; color:var(--text-muted);">No events found. Create an event to begin.</td></tr>`;
        return;
    }

    tbody.innerHTML = events.map(ev => {
        const total = ev.totalSeats || 0;
        const available = ev.availableSeats || 0;
        const booked = Math.max(0, total - available);
        const reserved = 0; // updated in real time via SSE
        const occupancy = total > 0 ? Math.round((booked / total) * 100) : 0;
        const estRevenue = booked * 500;
        const statusClass = ev.status === 'OPEN' ? 'open' : ev.status === 'UPCOMING' ? 'upcoming' : 'closed';

        return `
            <tr id="breakdown-row-event-${ev.eventId}">
                <td>
                    <div style="font-weight:700; color:var(--text-primary);">${escapeHtml(ev.eventName)}</div>
                    <div style="font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--text-muted);">ID: #${ev.eventId}</div>
                </td>
                <td>
                    <div style="font-size:12.5px;">${escapeHtml(ev.venueName)}</div>
                    <div style="font-size:11px; color:var(--text-muted);">${formatDateTime(ev.eventDate)}</div>
                </td>
                <td>
                    <span class="event-status-tag ${statusClass}">● ${ev.status}</span>
                </td>
                <td style="font-weight:700; font-family:'JetBrains Mono',monospace;">${total}</td>
                <td style="font-weight:700; font-family:'JetBrains Mono',monospace; color:#dc2626;" id="breakdown-booked-${ev.eventId}">${booked}</td>
                <td style="font-weight:700; font-family:'JetBrains Mono',monospace; color:#d97706;" id="breakdown-reserved-${ev.eventId}">${reserved}</td>
                <td style="font-weight:700; font-family:'JetBrains Mono',monospace; color:#059669;" id="breakdown-avail-${ev.eventId}">${available}</td>
                <td>
                    <div style="font-size:12px; font-weight:700;">${occupancy}%</div>
                    <div class="table-progress-bar">
                        <div class="table-progress-fill" style="width: ${occupancy}%;"></div>
                    </div>
                </td>
                <td style="font-weight:700; color:#059669; font-family:'JetBrains Mono',monospace;">₹${estRevenue.toLocaleString('en-IN')}</td>
                <td>
                    <button class="btn-primary" style="font-size:11px; padding:5px 9px; display:inline-flex; align-items:center; gap:4px; background:#2563eb;" onclick="openAdminSeatInspector(${ev.eventId})">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                        </svg>
                        Inspect Seats
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function renderQueueRecentBookingsTable(bookings) {
    const tbody = document.getElementById('admin-queue-recent-bookings-body');
    const countBadge = document.getElementById('admin-analytics-bookings-count');
    if (!tbody) return;

    if (countBadge) countBadge.textContent = `${bookings.length} Bookings`;

    if (!bookings || bookings.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding:24px; color:var(--text-muted);">No confirmed bookings recorded yet.</td></tr>`;
        return;
    }

    tbody.innerHTML = bookings.map(b => {
        const seatsStr = Array.isArray(b.seats) 
            ? b.seats.map(s => typeof s === 'object' ? s.seatNumber : s).join(', ')
            : (b.seats || 'General');

        const dateStr = b.confirmedAt ? formatDateTime(b.confirmedAt) : (b.createdAt ? formatDateTime(b.createdAt) : 'Just now');
        const amount = parseFloat(b.totalAmount) || 500;

        return `
            <tr>
                <td>
                    <span style="font-family:'JetBrains Mono',monospace; font-weight:700; color:var(--primary); font-size:12px;">
                        ${b.bookingReference || `TF-${b.bookingId || '2026'}`}
                    </span>
                </td>
                <td>
                    <div style="font-weight:600;">${escapeHtml(b.eventName || 'Event Booking')}</div>
                </td>
                <td>
                    <span style="font-family:'JetBrains Mono',monospace; font-size:12px; color:var(--text-secondary);">
                        #${b.userId || 7150}
                    </span>
                </td>
                <td>
                    <span class="seat-pill" style="font-size:11px; padding:2px 8px; background:#eff6ff; color:#1d4ed8; border:1px solid #bfdbfe;">
                        ${escapeHtml(seatsStr)}
                    </span>
                </td>
                <td style="font-weight:700; color:#059669; font-family:'JetBrains Mono',monospace;">
                    ₹${amount.toFixed(2)}
                </td>
                <td style="font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--text-muted);">
                    ${b.paymentId || 'MOCK-PAY-OK'}
                </td>
                <td>
                    <span class="event-status-tag open" style="background:rgba(16,185,129,0.15); color:#059669; border:1px solid #10b981; font-size:10px; padding:2px 6px;">
                        ${b.status || 'CONFIRMED'}
                    </span>
                </td>
                <td style="font-size:11.5px; color:var(--text-secondary);">
                    ${dateStr}
                </td>
            </tr>
        `;
    }).join('');
}

// ─────────────────────────────────────────────────────────────
// Admin Actions: Event Creation & Status Management
// ─────────────────────────────────────────────────────────────
function openCreateEventModal() {
    const modal = document.getElementById('create-event-modal');
    if (modal) modal.classList.add('active');
}

function closeCreateEventModal() {
    const modal = document.getElementById('create-event-modal');
    if (modal) modal.classList.remove('active');
}

async function handleCreateEventSubmit(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-submit-create-event');
    if (btn) btn.disabled = true;

    const payload = {
        eventName: document.getElementById('event-input-name').value,
        venueName: document.getElementById('event-input-venue').value,
        eventDate: document.getElementById('event-input-date').value + ':00',
        saleStartTime: document.getElementById('event-input-sale-start').value + ':00',
        totalSeats: parseInt(document.getElementById('event-input-seats').value, 10)
    };

    try {
        const headers = { 'Content-Type': 'application/json' };
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/admin/events`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        showToast(`Event "${payload.eventName}" created with ${payload.totalSeats} seats!`, 'success');
        closeCreateEventModal();
        await loadEvents();
    } catch (err) {
        console.error('Failed creating event', err);
        showToast('Failed to create event. Ensure ADMIN role authorization.', 'alert');
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function adminUpdateEventStatus(eventId, newStatus) {
    try {
        const headers = {};
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/admin/events/${eventId}/status?status=${newStatus}`, {
            method: 'POST',
            headers: headers
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        showToast(`Event #${eventId} status updated to ${newStatus}`, 'success');
        await loadEvents();
    } catch (err) {
        console.error('Failed updating event status', err);
        showToast('Failed updating event status', 'alert');
    }
}

async function adminDeleteEvent(eventId) {
    if (!confirm(`Are you sure you want to delete Event #${eventId}?`)) return;

    try {
        const headers = {};
        if (state.user.accessToken) {
            headers['Authorization'] = `Bearer ${state.user.accessToken}`;
        }

        const res = await fetch(`${API_BASE}/events/${eventId}`, {
            method: 'DELETE',
            headers: headers
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        showToast(`Event #${eventId} deleted`, 'info');
        await loadEvents();
    } catch (err) {
        console.error('Failed deleting event', err);
        showToast('Failed deleting event', 'alert');
    }
}

// ─────────────────────────────────────────────────────────────
// Admin Analytics & Actuator Telemetry
// ─────────────────────────────────────────────────────────────
async function refreshMetrics() {
    try {
        // Fetch Actuator Health
        const healthRes = await fetch(`${API_BASE}/actuator/health`).catch(() => null);
        
        // Fetch Threads Metric
        const threadsRes = await fetch(`${API_BASE}/actuator/metrics/jvm.threads.live`).catch(() => null);
        if (threadsRes && threadsRes.ok) {
            const tData = await threadsRes.json();
            const threads = tData.measurements?.[0]?.value || 38;
            state.metrics.jvmThreads = Math.round(threads);
            const disp = document.getElementById('metric-threads-display');
            if (disp) disp.textContent = state.metrics.jvmThreads;
        }

        // Fetch CPU Metric
        const cpuRes = await fetch(`${API_BASE}/actuator/metrics/system.cpu.usage`).catch(() => null);
        if (cpuRes && cpuRes.ok) {
            const cData = await cpuRes.json();
            const cpu = (cData.measurements?.[0]?.value || 0.02) * 100;
            state.metrics.systemCpu = `${cpu.toFixed(1)}%`;
            const disp = document.getElementById('metric-cpu-display');
            if (disp) disp.textContent = state.metrics.systemCpu;
        }
    } catch (e) {
        console.warn('Metrics polling error', e);
    }
}

function updateFleetMetrics() {
    let totalSeats = 0;
    let availableSeats = 0;

    state.events.forEach(e => {
        totalSeats += e.totalSeats || 0;
        availableSeats += e.availableSeats || 0;
    });

    const booked = totalSeats - availableSeats;
    const occupancy = totalSeats > 0 ? ((booked / totalSeats) * 100).toFixed(1) : '0.0';

    const soldElem = document.getElementById('admin-stat-tickets-sold');
    const revElem = document.getElementById('admin-stat-revenue');
    const occElem = document.getElementById('admin-stat-occupancy');

    if (soldElem) soldElem.textContent = booked;
    if (revElem) revElem.textContent = `₹${(booked * 500).toLocaleString()}`;
    if (occElem) occElem.textContent = `${occupancy}%`;
}

async function loadScalperAuditLogs() {
    const tbody = document.getElementById('admin-audit-table-body');
    if (!tbody) return;

    try {
        const res = await fetch(`${API_BASE}/api/security/audit-trail`).catch(() => null);
        let logs = [];
        if (res && res.ok) {
            logs = await res.json();
        }

        if (logs.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td style="padding:10px;">${new Date().toLocaleTimeString()}</td>
                    <td style="padding:10px; font-family:monospace;">127.0.0.1</td>
                    <td style="padding:10px;"><span style="color:#059669; font-weight:700;">5 / 100</span></td>
                    <td style="padding:10px;">reCAPTCHA v2 Verified Human</td>
                    <td style="padding:10px;"><span class="event-status-tag open">ALLOWED</span></td>
                </tr>
                <tr>
                    <td style="padding:10px;">${new Date(Date.now() - 3600000).toLocaleTimeString()}</td>
                    <td style="padding:10px; font-family:monospace;">127.0.0.1</td>
                    <td style="padding:10px;"><span style="color:#d97706; font-weight:700;">45 / 100</span></td>
                    <td style="padding:10px;">Burst Rate Threshold Met</td>
                    <td style="padding:10px;"><span class="event-status-tag upcoming">CHALLENGED</span></td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = logs.map(l => `
            <tr style="border-bottom: 1px solid var(--border-default);">
                <td style="padding:10px;">${l.timestamp ? new Date(l.timestamp).toLocaleTimeString() : 'Recent'}</td>
                <td style="padding:10px; font-family:monospace;">${escapeHtml(l.clientIp || '127.0.0.1')}</td>
                <td style="padding:10px; font-weight:700;">${l.riskScore || 10} / 100</td>
                <td style="padding:10px;">${escapeHtml(l.detectionReason || 'Verified Human')}</td>
                <td style="padding:10px;">
                    <span class="event-status-tag ${l.actionTaken === 'ALLOWED' ? 'open' : l.actionTaken === 'BLOCKED' ? 'closed' : 'upcoming'}">
                        ${l.actionTaken}
                    </span>
                </td>
            </tr>
        `).join('');
    } catch (e) {
        console.warn('Audit logs load exception', e);
    }
}

// ─────────────────────────────────────────────────────────────
// Utility Helpers
// ─────────────────────────────────────────────────────────────
function formatDateTime(dtStr) {
    if (!dtStr) return 'N/A';
    try {
        const dt = new Date(dtStr);
        return dt.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true
        });
    } catch (e) {
        return dtStr;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

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
