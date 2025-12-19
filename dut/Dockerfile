FROM python:3.12-slim

# Ensure logs flush immediately
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# Copy bully process implementation
COPY bully.py /app/bully.py

# Install dependencies
RUN pip install --no-cache-dir flask requests

# By default, just run bully.py; arguments are passed from docker-compose
ENTRYPOINT ["python", "bully.py"]
