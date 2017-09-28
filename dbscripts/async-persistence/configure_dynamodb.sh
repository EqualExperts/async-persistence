#!/bin/sh

aws --endpoint-url=http://localhost:8000 dynamodb create-table --cli-input-json file://asyncTaskCache.json

aws --endpoint-url=http://localhost:8000 dynamodb update-time-to-live --table-name asyncTaskCache --time-to-live-specification file://ttl.json
## aws --endpoint-url=http://localhost:8000 dynamodb update-time-to-live --table-name asyncTaskCache --time-to-live-specification Enabled=true,AttributeName=start
