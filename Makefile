.PHONY: build up down logs smoke test clean

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f --tail=100

smoke:
	@echo "Submitting a CDQL query through the Query Engine..."
	curl -s -X POST http://localhost:8080/api/query \
	  -H 'Content-Type: application/json' \
	  -d '{"query": "SELECT roadwork.congestion WHERE roadwork.location WITHIN 5km OF (-37.8218,145.0421) AND roadwork.freshness >= 0.38"}'

test:
	cd capme && python -m pytest tests/ -v
	cd cfms  && python -m pytest tests/ -v
	cd dcmf  && mvn -q test
	cd vacf  && mvn -q test
	cd eccf  && mvn -q test

clean:
	docker compose down -v
	find . -name '__pycache__' -type d -exec rm -rf {} +
	find . -name '*.pyc' -delete
	find . -name 'target' -type d -exec rm -rf {} +
