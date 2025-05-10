from flask import Flask, render_template, Response, request, jsonify
import cv2
import numpy as np
import threading

app = Flask(__name__)
latest_frame = None
lock = threading.Lock()

# 더미 GPS 좌표 (실제로는 업데이트 필요)
current_gps = {"lat": 37.5665, "lng": 126.9780}

@app.route('/')
def index():
    return render_template('main.html')


@app.route('/upload_frame', methods=['POST'])
def upload_frame():
    global latest_frame

    if 'frame' not in request.files:
        return 'No frame part',400
    
    file = request.files['frame']
    npimg = np.frombuffer(file.read(), np.uint8)
    frame = cv2.imdecode(npimg, cv2.IMREAD_COLOR)

    with lock:
        latest_frame = frame
    return 'Frame received', 200


def generate_video_feed():
    global latest_frame

    while True:
        with lock:
            if latest_frame is None:
                continue
            ret, buffer = cv2.imencode('.jpg', latest_frame)
            frame = buffer.tobytes()
        
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')


def generate_video():
    cap = cv2.VideoCapture(0)  # 노트북 웹캠 or RTSP 가능
    while True:
        success, frame = cap.read()
        if not success:
            break
        _, buffer = cv2.imencode('.jpg', frame)
        frame = buffer.tobytes()
        yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')



@app.route('/video_feed')
def video_feed():
    return Response(generate_video(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')
    #return Response(generate_video_feed(),mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/gps')
def gps():
    # 예시용 GPS 좌표
    return jsonify(current_gps)



@app.route('/send_message', methods=['POST'])
def send_message():
    msg = request.form['message']
    print("Received message:", msg)
    return jsonify({'status': 'success', 'message': msg})



if __name__ == '__main__':
    app.run(debug=True)