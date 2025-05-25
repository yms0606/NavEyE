// 지도 초기화
let map = L.map('map').setView([37.5665, 126.9780], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'Map data © OpenStreetMap contributors'
}).addTo(map);
let marker = L.marker([37.5665, 126.9780]).addTo(map);

// GPS 위치 업데이트
function updateGPS() {
    fetch('/gps')
        .then(res => res.json())
        .then(data => {
            marker.setLatLng([data.lat, data.lng]);
            map.setView([data.lat, data.lng]);
        });
}
setInterval(updateGPS, 5000);

// 메시지 전송 기능
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

// 영상 스트리밍 대기 중 텍스트 처리
window.onload = function () {
    const videoStream = document.getElementById("video-stream");

    // 대기 텍스트 오버레이 생성
    const placeholder = document.createElement("div");
    placeholder.id = "video-placeholder";
    placeholder.style.position = "absolute";
    placeholder.style.top = "50%";
    placeholder.style.left = "50%";
    placeholder.style.transform = "translate(-50%, -50%)";
    placeholder.style.fontSize = "1.5rem";
    placeholder.style.color = "#888";
    placeholder.style.pointerEvents = "none";
    placeholder.style.textAlign = "center";
    placeholder.style.zIndex = "1";

    const container = videoStream.parentElement;
    container.style.position = "relative";
    container.appendChild(placeholder);

    // 영상 로딩 완료 시 텍스트 숨김
    videoStream.onload = () => {
        placeholder.style.display = "none";
    };

    // 일정 시간 지나도 로딩 안 되면 표시 유지
    setTimeout(() => {
        if (!videoStream.complete || videoStream.naturalWidth === 0) {
            placeholder.style.display = "block";
        }
    }, 2000);
};
