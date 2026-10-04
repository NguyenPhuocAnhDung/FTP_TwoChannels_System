// Web Admin & Protocol Visualizer Client Script
let animationOffset = 0;

function switchTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
    
    document.getElementById(tabId).classList.add('active');
    event.target.classList.add('active');

    if (tabId === 'tabFiles') fetchFiles();
    if (tabId === 'tabLogs') fetchLogs();
    if (tabId === 'tabPackets') fetchPackets();
}

// Fetch Metrics from Embedded Server
async function fetchStats() {
    try {
        const res = await fetch('/api/stats');
        const data = await res.json();

        document.getElementById('valPort').innerText = 'Port ' + (data.controlPort || 2121);
        document.getElementById('valClients').innerText = data.activeClients || 0;
        
        const total = data.totalTransferredBytes || 0;
        if (total < 1024 * 1024) {
            document.getElementById('valTransferred').innerText = (total / 1024).toFixed(2) + ' KB';
        } else {
            document.getElementById('valTransferred').innerText = (total / (1024 * 1024)).toFixed(2) + ' MB';
        }

        document.getElementById('valSpeed').innerText = (data.throughputKbps || 0).toFixed(2) + ' KB/s';

        const badgeServer = document.getElementById('badgeServer');
        if (data.running) {
            badgeServer.className = 'badge badge-success';
            badgeServer.innerText = 'SERVER: ONLINE (Port ' + data.controlPort + ')';
        } else {
            badgeServer.className = 'badge';
            badgeServer.style.background = '#ef4444';
            badgeServer.innerText = 'SERVER: STOPPED';
        }

        const badgeDb = document.getElementById('badgeDb');
        if (data.databaseConnected) {
            badgeDb.className = 'badge badge-info';
            badgeDb.innerText = 'MYSQL: CONNECTED (' + data.databaseName + ')';
        } else {
            badgeDb.className = 'badge';
            badgeDb.style.background = '#f59e0b';
            badgeDb.innerText = 'MYSQL: FALLBACK (IN-MEMORY)';
        }
    } catch (e) {
        console.warn('Khong the lay chi so tu server:', e);
    }
}

// Fetch Files
async function fetchFiles() {
    try {
        const res = await fetch('/api/files');
        const list = await res.json();
        const tbody = document.getElementById('fileTableBody');
        tbody.innerHTML = '';

        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">Thư mục trống.</td></tr>';
            return;
        }

        list.forEach(f => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${f.name}</strong></td>
                <td>${f.formattedSize}</td>
                <td>${f.isDirectory ? '📁 Thư mục' : '📄 Tệp tin'}</td>
                <td>${f.modified}</td>
                <td><span style="color:#38bdf8; font-size:0.85rem;">Sẵn sàng qua FTP RETR</span></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}

// Fetch Audit Logs
async function fetchLogs() {
    try {
        const res = await fetch('/api/logs');
        const logs = await res.json();
        const tbody = document.getElementById('logTableBody');
        tbody.innerHTML = '';

        if (!logs || logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;">Chưa có nhật ký hoạt động nào.</td></tr>';
            return;
        }

        logs.forEach(l => {
            const tr = document.createElement('tr');
            const statusColor = l.status === 'SUCCESS' ? '#34d399' : '#f87171';
            tr.innerHTML = `
                <td>${l.createdAt}</td>
                <td><strong>${l.username}</strong></td>
                <td><code>${l.clientIp}</code></td>
                <td><span class="badge" style="background:#1e293b; border:1px solid #475569;">${l.action}</span></td>
                <td>${l.filename || '-'}</td>
                <td>${l.fileSizeBytes > 0 ? (l.fileSizeBytes / 1024).toFixed(1) + ' KB' : '-'}</td>
                <td>${l.speedKbps > 0 ? l.speedKbps.toFixed(1) + ' KB/s' : '-'}</td>
                <td style="color:${statusColor}; font-weight:600;">${l.status}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}

// Fetch Packets
async function fetchPackets() {
    try {
        const res = await fetch('/api/packets');
        const pkts = await res.json();
        const tbody = document.getElementById('packetTableBody');
        tbody.innerHTML = '';

        if (!pkts || pkts.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">Chưa có gói tin nào ghi nhận.</td></tr>';
            return;
        }

        pkts.forEach(p => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${p.timestamp}</td>
                <td><span class="badge badge-info">${p.layer}</span></td>
                <td><strong>${p.protocol}</strong></td>
                <td><code>${p.flags}</code></td>
                <td>${p.source} ➔ ${p.dest}</td>
                <td>${p.size} B</td>
                <td><code>${escapeHtml(p.content)}</code></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Canvas Network Topology Animation
function drawTopology() {
    const canvas = document.getElementById('topoCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;

    ctx.clearRect(0, 0, w, h);

    const clientX = 140, clientY = h / 2;
    const serverX = w / 2, serverY = h / 2;
    const dbX = w - 160, dbY = h / 2 - 50;
    const storageX = w - 160, storageY = h / 2 + 50;

    // 1. Line Control Channel
    ctx.lineWidth = 3;
    ctx.strokeStyle = '#38bdf8';
    ctx.beginPath();
    ctx.moveTo(clientX + 50, clientY - 15);
    ctx.lineTo(serverX - 60, serverY - 15);
    ctx.stroke();

    // 2. Line Data Channel
    ctx.setLineDash([8, 6]);
    ctx.strokeStyle = '#fb923c';
    ctx.beginPath();
    ctx.moveTo(clientX + 50, clientY + 15);
    ctx.lineTo(serverX - 60, serverY + 15);
    ctx.stroke();
    ctx.setLineDash([]);

    // 3. Line Server to MySQL & Storage
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#c084fc';
    ctx.beginPath();
    ctx.moveTo(serverX + 60, serverY - 15);
    ctx.lineTo(dbX - 50, dbY);
    ctx.stroke();

    ctx.strokeStyle = '#34d399';
    ctx.beginPath();
    ctx.moveTo(serverX + 60, serverY + 15);
    ctx.lineTo(storageX - 50, storageY);
    ctx.stroke();

    // Labels for Channels
    ctx.font = '600 11px Inter';
    ctx.fillStyle = '#38bdf8';
    ctx.fillText('KÊNH 1: TCP Control (Port 2121) [Lệnh/Phản hồi]', clientX + 45, clientY - 24);

    ctx.fillStyle = '#fb923c';
    ctx.fillText('KÊNH 2: TCP Data (Port Động 300xx) [Byte Stream]', clientX + 45, clientY + 34);

    // Animated packet dot
    animationOffset = (animationOffset + 2) % 300;
    const packetX = (clientX + 50) + animationOffset;
    if (packetX < serverX - 60) {
        ctx.fillStyle = '#facc15';
        ctx.beginPath();
        ctx.arc(packetX, clientY - 15, 6, 0, Math.PI * 2);
        ctx.fill();
    }

    // Draw Nodes
    drawNode(ctx, clientX, clientY, 'FTP CLIENT', 'Desktop / CLI', '#0284c7');
    drawNode(ctx, serverX, serverY, 'FTP SERVER CORE', 'Java 23 (RFC 959)', '#059669');
    drawNode(ctx, dbX, dbY, 'MYSQL DATABASE', 'Port 3306 (Audit/Auth)', '#9333ea');
    drawNode(ctx, storageX, storageY, 'LOCAL STORAGE', 'File System Root', '#0d9488');

    requestAnimationFrame(drawTopology);
}

function drawNode(ctx, cx, cy, title, sub, color) {
    const nw = 120, nh = 56;
    const x = cx - nw / 2;
    const y = cy - nh / 2;

    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.roundRect(x, y, nw, nh, 10);
    ctx.fill();

    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    ctx.fillStyle = '#ffffff';
    ctx.font = '700 11px Inter';
    ctx.textAlign = 'center';
    ctx.fillText(title, cx, cy - 3);

    ctx.font = '500 10px Inter';
    ctx.fillStyle = '#e2e8f0';
    ctx.fillText(sub, cx, cy + 14);
}

// Initial Calls & Interval
window.addEventListener('DOMContentLoaded', () => {
    fetchStats();
    fetchFiles();
    fetchLogs();
    fetchPackets();
    drawTopology();

    setInterval(fetchStats, 2000);
    setInterval(() => {
        const activeTab = document.querySelector('.tab-content.active');
        if (activeTab && activeTab.id === 'tabPackets') fetchPackets();
        if (activeTab && activeTab.id === 'tabLogs') fetchLogs();
    }, 3000);
});
