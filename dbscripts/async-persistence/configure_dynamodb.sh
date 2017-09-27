#!/bin/sh

aws --endpoint-url=http://localhost:8000 dynamodb create-table --cli-input-json file://asynctaskcache.json
