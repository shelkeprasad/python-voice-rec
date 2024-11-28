from flask import Flask, request, jsonify
import tensorflow as tf
import librosa
import numpy as np
import os
app = Flask(__name__)
# Directory to store audio samples
UPLOADS_DIR = "uploads"
os.makedirs(UPLOADS_DIR, exist_ok=True)
# Path to save/load the model
MODEL_PATH = "speaker_model.h5"
model = None  # Initialize a global model variable
label_map = {}  # Global label map for speaker IDs to model indices
# Load or initialize the model
if os.path.exists(MODEL_PATH):
    try:
        model = tf.keras.models.load_model(MODEL_PATH)
        print("Model loaded successfully.")
    except Exception as e:
        print(f"Error loading model: {e}")
# Helper function to preprocess audio
def load_audio_file(file_path):
    try:
        y, sr = librosa.load(file_path, sr=16000)
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
        # Ensure fixed size input
        mfcc = np.pad(mfcc, ((0, 0), (0, max(0, 100 - mfcc.shape[1]))), mode="constant")[:, :100]
        mfcc = np.expand_dims(mfcc, axis=-1)  # Add channel dimension
        return np.expand_dims(mfcc, axis=0)  # Add batch dimension
    except Exception as e:
        print(f"Error processing audio file {file_path}: {e}")
        raise e
# Endpoint to register a new speaker
@app.route("/register", methods=["POST"])
def register_speaker():
    try:
        if "file" not in request.files or "speaker_id" not in request.form:
            return jsonify({"error": "Missing speaker ID or audio file"}), 400
        speaker_id = request.form["speaker_id"]
        audio_file = request.files["file"]
        if not speaker_id.isdigit():
            return jsonify({"error": "Speaker ID must be numeric"}), 400
        speaker_dir = os.path.join(UPLOADS_DIR, f"speaker_{speaker_id}")
        os.makedirs(speaker_dir, exist_ok=True)
        file_path = os.path.join(speaker_dir, audio_file.filename)
        audio_file.save(file_path)
        return jsonify({"message": f"Audio saved for speaker {speaker_id}"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
# Endpoint to retrain the model
@app.route("/retrain", methods=["POST"])
def retrain_model():
    try:
        global model, label_map
        X, y = [], []
        for speaker_dir in os.listdir(UPLOADS_DIR):
            try:
                speaker_id = int(speaker_dir.split("_")[1])
                print(f"Processing speaker: {speaker_id}")
                speaker_path = os.path.join(UPLOADS_DIR, speaker_dir)
                for audio_file in os.listdir(speaker_path):
                    file_path = os.path.join(speaker_path, audio_file)
                    mfcc = load_audio_file(file_path)
                    X.append(mfcc)
                    y.append(speaker_id)
            except Exception as e:
                print(f"Error processing speaker directory {speaker_dir}: {e}")
        X = np.vstack(X)  # Combine all samples into one array
        y = np.array(y)
        unique_labels = sorted(np.unique(y))
        label_map = {label: idx for idx, label in enumerate(unique_labels)}
        y = np.array([label_map[label] for label in y])
        print(f"Final Label Map: {label_map}")
        num_classes = len(unique_labels)
        if model is None:
            model = tf.keras.Sequential([
                tf.keras.layers.Conv2D(32, (2, 2), activation="relu", input_shape=(X.shape[1], X.shape[2], X.shape[3])),
                tf.keras.layers.MaxPooling2D((2, 2)),
                tf.keras.layers.Conv2D(64, (2, 2), activation="relu"),
                tf.keras.layers.MaxPooling2D((2, 2)),
                tf.keras.layers.Flatten(),
                tf.keras.layers.Dense(128, activation="relu"),
                tf.keras.layers.Dense(num_classes, activation="softmax"),
            ])
        else:
            model.pop()  # Remove the old output layer
            model.add(tf.keras.layers.Dense(num_classes, activation="softmax"))
        model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
        model.fit(X, y, epochs=5, batch_size=32)
        model.save(MODEL_PATH)
        print("Model retrained and saved successfully.")
        return jsonify({"message": "Model retrained successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
# Endpoint to recognize a speaker
@app.route("/recognize", methods=["POST"])
def recognize_speaker():
    try:
        if "file" not in request.files:
            return jsonify({"error": "No file uploaded"}), 400
        audio_file = request.files["file"]
        file_path = os.path.join(UPLOADS_DIR, audio_file.filename)
        audio_file.save(file_path)
        if model is None:
            return jsonify({"error": "Model not found. Retrain first."}), 500
        mfcc = load_audio_file(file_path)
        predictions = model.predict(mfcc)
        predicted_index = int(np.argmax(predictions))  # Convert int64 to int
        confidence = float(predictions[0][predicted_index])  # Convert float64 to float
        reverse_label_map = {v: k for k, v in label_map.items()}
        if predicted_index not in reverse_label_map:
            return jsonify({"error": "Prediction index not in label map. Retrain the model."}), 500
        predicted_label = reverse_label_map[predicted_index]
        return jsonify({
            "predicted_label": int(predicted_label),  # Convert to Python int
            "confidence": confidence  # Already a float
        }), 200
    except Exception as e:
        print(f"Error in /recognize: {e}")  # Log error to server
        return jsonify({"error": str(e)}), 500
if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=5000)