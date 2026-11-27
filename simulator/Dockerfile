FROM python:3.15-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY simulator.py .
# -u prints logs right away instead of buffering them
CMD ["python", "-u", "simulator.py"]
