from flask import Flask, render_template, Response, request, jsonify
import cv2
import numpy as np
import threading
from ultralytics import YOLO
import time
from collections import deque
import queue
import torch
torch.set_num_threads(1)
cv2.setNumThreads(0)


app = Flask(__name__)
latest_frame = None
latest_message = None
lock = threading.Lock()

img_queue = queue.Queue()
inference_img_queue = queue.Queue()
inference_info_queue  = queue.Queue()
alert_queue = queue.Queue()

program_start = False

print('model loading . . .')
model = YOLO('yolov8n.pt')
print(f"model loading success {model.device}, {model.task}")
# x = np.zeros((480, 640, 3), dtype=np.uint8)
# print("np:", x.shape, x.dtype)
# r = model(x)
# print(r)


# 더미 GPS 좌표 (실제로는 업데이트 필요)
current_gps = {"lat": 37.5665, "lng": 126.9780}

@app.route('/')
def index():
    return render_template('main.html')



@app.route('/upload_frame', methods=['POST'])
def upload_frame():
    global model, latest_frame, img_queue, inference_info_queue, program_start

    if 'frame' not in request.files:
        return 'No frame part',400
    
    file = request.files['frame']
    npimg = np.frombuffer(file.read(), np.uint8)

    frame = cv2.imdecode(npimg, cv2.IMREAD_COLOR)
    frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
    frame = cv2.resize(frame, (320, 320))

    results = model.track(source=frame, conf=0.8, imgsz=320, tracker='cfg/trackers/bytetrack.yaml')[0]


    for box in results.boxes:
        x1, y1, x2, y2 = map(int, box.xyxy[0])
        label = model.names[int(box.cls[0])]
        confidence = float(box.conf[0])
        id = int(box.id[0]) if box.id is not None else -1

        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0))
        cv2.putText(frame, f"{label} {confidence:.2f}", (x1, y1-10),
                    cv2.FONT_HERSHEY_COMPLEX, 0.5, (0, 255, 0))

    #inference_img_queue.put(frame)
    
    #with lock:
        #latest_frame = frame
        #inference_img_queue.popleft(frame)
    # img_queue.put(frame)

    print("frame added to img_queue")

    img_queue.put(frame)
    inference_info_queue.put(results.boxes)

    if not program_start:
        program_start = True
        print("starting thread")
        th =  threading.Thread(target=inference_img)
        th.start()
    
  

    return 'Frame received', 200



def inference_img():
    global model, img_queue, inference_img_queue, inference_info_queue, alert_queue
    
    imgsz = 320
    track_dict = {}
    call_ids = []
    last_TTS_time = time.time()

    while True:
            
            for box in inference_info_queue.get():
                
                curr_time = time.time()
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                center_x = (x1+x2)/2
                center_y = (y1+y2)/2

                if ((x2-x1)*(y2-y1) / imgsz**2 > 0.2) and imgsz*0.25<center_x<imgsz*0.75 and imgsz*0.25<center_y<imgsz*0.75:
                    
                    id = int(box.id[0])
                    label = model.names[int(box.cls[0])]

                    if (id,label) not in track_dict:
                        track_dict[(id,label)] = curr_time

                    elif curr_time-track_dict[(id,label)] > 1.5 and curr_time-last_TTS_time > 3.0:
                        
                        if (id,label) not in call_ids:
                            call_ids.append((id,label))    
                            last_TTS_time = curr_time

                            alert_info = {
                                "label": model.names[int(box.cls[0])]
                            }

                            alert_queue.put(alert_info)

            frame = img_queue.get()
            inference_img_queue.put(frame)


            


def generate_video_feed():
    global inference_img_queue

    while True:
        if not inference_img_queue:
            continue
        
        frame = inference_img_queue.get()
        #with lock:
        #    frame = inference_img_queue.popleft()

        frame = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
        ret, buffer = cv2.imencode('.jpg', frame)
        frame = buffer.tobytes()
        
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')


@app.route("/alert", methods=["GET"])
def get_alert():
    global alert_queue
    if not alert_queue.empty():
        return jsonify(alert_queue.get())
    else:
        return jsonify({})


@app.route('/video_feed')
def video_feed():
    return Response(generate_video_feed(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')
    #return Response(generate_video_feed(),mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/update_gps', methods=['POST'])
def update_gps():
    global current_gps
    current_gps['lat'] = request.form.get('lat')
    current_gps['lng'] = request.form.get('lon')

    return "GPS updated", 200

@app.route('/gps')
def gps():
    global current_gps
    # 예시용 GPS 좌표
    return jsonify(current_gps)



@app.route('/send_message', methods=['POST'])
def send_message():
    global latest_message
    msg = request.form['message']
    latest_message = msg
    print("Received message:", msg)
    return jsonify({'status': 'success', 'message': msg})

@app.route('/get_message', methods=['GET'])
def get_message():
    global latest_message
    if latest_message:
        msg = latest_message
        latest_message = None
        return jsonify({'message':msg})
    return jsonify({'message': None})


if __name__ == '__main__':
    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)