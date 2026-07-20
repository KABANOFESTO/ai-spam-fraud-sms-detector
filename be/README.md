## AI-Based Spam/Fraud SMS Detector Backend

Django REST API for authenticating users, classifying SMS messages, tracking analysis history, generating dashboard stats, and managing fraud reports.

### Core API Areas

- `POST /api/auth/register/` register a user and receive JWT tokens
- `POST /api/auth/login/` login with email/password and receive JWT tokens
- `POST /api/auth/logout/` blacklist a refresh token
- `POST /api/analysis/analyze/` classify one SMS message
- `POST /api/analysis/bulk-analyze/` classify multiple SMS messages
- `GET /api/analysis/history/` view analysis history
- `GET /api/analysis/stats/` view personal analysis stats
- `GET /api/analysis/dashboard/` admin dashboard summary
- `POST /api/reports/` submit a fraud report
- `GET /api/reports/` list reports
- `GET /api/audit-log/audit-logs/` admin audit trail
- `GET /api/health/` health check
- `GET /api/docs/` Swagger UI

### Mobile Integration Notes

- Authentication uses JWT bearer tokens.
- The analyze endpoint returns prediction, confidence, risk score, matched signals, and explanation.
- History, report, and dashboard endpoints are pagination/filter friendly.
- Profile images are served from `MEDIA_URL`.

### Local Setup

1. Create and activate a virtual environment.
2. Install dependencies from `requirements.txt`.
3. Configure `.env`.
4. Run migrations.
5. Start the server with `python manage.py runserver`.

### Training The SMS Detector

1. Use the bundled starter dataset at `analysis/data/sms_training_data.csv` or provide your own labeled CSV with `message` and `label` columns.
2. Train the model with:

```bash
python manage.py train_sms_detector
```

3. Optionally override the data or artifact path:

```bash
python manage.py train_sms_detector --data path/to/data.csv --artifact artifacts/sms_detector.joblib --force
```

4. After training, the analyze endpoints will use the saved sklearn artifact instead of heuristics.
