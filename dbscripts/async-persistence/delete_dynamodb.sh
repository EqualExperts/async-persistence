#!/bin/sh

aws --endpoint-url=http://localhost:8000 dynamodb delete-table --table-name asyncTaskCache