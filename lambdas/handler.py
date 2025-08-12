import os, json, base64, math, time, uuid
import boto3
from urllib.parse import parse_qs

S3_BUCKET = os.environ["S3_BUCKET"]
COLLECTION_ID = os.environ["COLLECTION_ID"]
TABLE_SESSIONS = os.environ["TABLE_SESSIONS"]
TABLE_ATTENDANCE = os.environ["TABLE_ATTENDANCE"]

s3 = boto3.client("s3")
rek = boto3.client("rekognition")
dynamodb = boto3.resource("dynamodb")
sessions_tb = dynamodb.Table(TABLE_SESSIONS)
att_tb = dynamodb.Table(TABLE_ATTENDANCE)

def _now_ms(): return int(time.time() * 1000)

def _haversine_m(lat1, lon1, lat2, lon2):
    R=6371000.0
    from math import radians,sin,cos,atan2,sqrt
    dLat, dLon = radians(lat2-lat1), radians(lon2-lon1)
    a = sin(dLat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dLon/2)**2
    return 2*R*atan2(sqrt(a), sqrt(1-a))

def response(code, body, headers=None):
    h = {"Content-Type":"application/json"}
    if headers: h.update(headers)
    return {"statusCode": code, "headers": h, "body": json.dumps(body)}

def parse_multipart(event):
    # API Gateway HTTP API with default proxy may deliver base64 body
    body = base64.b64decode(event["body"]) if event.get("isBase64Encoded") else event["body"].encode()
    content_type = event["headers"].get("content-type") or event["headers"].get("Content-Type")
    import email
    msg = email.message_from_bytes(b"Content-Type: "+content_type.encode()+b"\r\n\r\n"+body)
    fields = {}
    file_bytes = None
    file_name = None
    for part in msg.walk():
        if part.get_content_disposition() == "form-data":
            name = part.get_param("name", header="content-disposition")
            filename = part.get_param("filename", header="content-disposition")
            payload = part.get_payload(decode=True)
            if filename:
                file_bytes = payload
                file_name = filename
            else:
                fields[name] = payload.decode()
    return fields, file_bytes, file_name

# ---------- Session ----------
def session_handler(event, context):
    method = event["requestContext"]["http"]["method"]
    if method == "POST":  # /session/start
        body = json.loads(event["body"])
        classId = body["classId"]
        item = {
            "classId": classId,
            "centerLat": float(body["centerLat"]),
            "centerLon": float(body["centerLon"]),
            "radiusMeters": int(body.get("radiusMeters", 10)),
            "expiresAtEpochMs": _now_ms() + 60_000 * int(body.get("durationMinutes", 10))
        }
        sessions_tb.put_item(Item=item)
        return response(200, {"ok": True})
    else:  # GET /session/current?classId=...
        classId = event["queryStringParameters"]["classId"]
        res = sessions_tb.get_item(Key={"classId": classId})
        if "Item" not in res: return response(404, {"message":"No active session"})
        return response(200, res["Item"])

# ---------- Enroll ----------
def enroll_handler(event, context):
    fields, file_bytes, file_name = parse_multipart(event)
    studentId = fields.get("studentId")
    if not studentId or not file_bytes:
        return response(400, {"ok": False, "message":"studentId and image required"})

    key = f"students/{studentId}.jpg"
    s3.put_object(Bucket=S3_BUCKET, Key=key, Body=file_bytes, ContentType="image/jpeg", ACL="private")

    # (Optional) also index into Rekognition collection for future search
    try:
        rek.create_collection(CollectionId=COLLECTION_ID)
    except rek.exceptions.ResourceAlreadyExistsException:
        pass
    rek.index_faces(
        CollectionId=COLLECTION_ID,
        Image={"Bytes": file_bytes},
        ExternalImageId=studentId,
        DetectionAttributes=[]
    )
    return response(200, {"ok": True, "imageKey": key})

# ---------- Check-in ----------
def checkin_handler(event, context):
    fields, file_bytes, _ = parse_multipart(event)
    required = ["classId", "lat", "lon", "studentId"]
    if any(k not in fields for k in required) or not file_bytes:
        return response(400, {"ok": False, "message":"missing fields"})

    classId = fields["classId"]
    lat, lon = float(fields["lat"]), float(fields["lon"])
    studentId = fields["studentId"]

    s = sessions_tb.get_item(Key={"classId": classId}).get("Item")
    if not s: return response(200, {"ok": False, "message":"No active session"})
    if _now_ms() > int(s["expiresAtEpochMs"]): return response(200, {"ok": False, "message":"Window expired"})

    dist = _haversine_m(lat, lon, float(s["centerLat"]), float(s["centerLon"]))
    if dist > float(s["radiusMeters"]):
        return response(200, {"ok": False, "message": f"Outside radius ({dist:.1f}m)"})

    # CompareFaces with enrollment image from S3
    ref_key = f"students/{studentId}.jpg"
    try:
        ref_obj = s3.get_object(Bucket=S3_BUCKET, Key=ref_key)
        ref_bytes = ref_obj["Body"].read()
    except Exception:
        return response(200, {"ok": False, "message":"No enrolled reference"})

    cmp = rek.compare_faces(
        SourceImage={"Bytes": ref_bytes},
        TargetImage={"Bytes": file_bytes},
        SimilarityThreshold=90.0
    )
    matches = cmp.get("FaceMatches", [])
    if not matches:
        return response(200, {"ok": False, "message":"Face mismatch"})

    similarity = float(matches[0]["Similarity"])

    # store attendance
    sessionId = f"{classId}#{int(s['expiresAtEpochMs'])}"
    att_tb.put_item(Item={
        "sessionId": sessionId,
        "studentId": studentId,
        "timestamp": _now_ms(),
        "similarity": similarity,
        "lat": lat, "lon": lon
    })

    return response(200, {"ok": True, "matchConfidence": similarity})
