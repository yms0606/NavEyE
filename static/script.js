// 지도 초기화
let map = L.map('map').setView([37.5665, 126.9780], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'Map data © OpenStreetMap contributors'
}).addTo(map);
let marker = L.marker([37.5665, 126.9780]).addTo(map);

function updateGPS() {
    fetch('/gps')
        .then(res => res.json())
        .then(data => {
            marker.setLatLng([data.lat, data.lng]);
            map.setView([data.lat, data.lng]);
        });
}
setInterval(updateGPS, 5000);

function sendMessage() {
    const msg = document.getElementById('message-input').value;
    fetch('/send_message', {
        method: 'POST',
        body: new URLSearchParams({ message: msg }),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    }).then(res => res.json())
      .then(data => {
          alert('전송 완료: ' + data.message);
          document.getElementById('message-input').value = '';
      });
}
