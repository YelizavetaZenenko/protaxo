<div align="center">
  <img src="src/main/resources/images/logo.png" alt="ProTaxo" width="220">

  # ProTaxo ERP

  Internal management system for a tachograph calibration and heavy-vehicle repair shop.
</div>

---

## What it is

ProTaxo is an ERP system for a tachograph service center: work orders, contracts, a catalog of goods and services, clients, a fleet of vehicles and tachographs, plus a full tachograph calibration protocol module built to the official regulatory form.

Built from the ground up — from business logic to production infrastructure — and deployed in real-world use on a dedicated server.

## Features

- **Work orders** — the sales document for goods/services, with automatic total calculation, stock deduction, and PDF generation (work order, VAT-itemized invoice, act of completed works)
- **Tachograph calibration protocols** — the full official form (11 sections, a 13-row results table), linked to a work order and the client's real tachograph, with automatic PDF generation
- **QR-code labels** — a dedicated Print Agent prints a label on a thermal printer (TSPL); the QR code opens a public verification page for the protocol, reachable by anyone without logging in (via Tailscale Funnel)
- **Reference data**: clients, vehicles, drivers, tachographs, a catalog of goods and services, contracts
- **Role-based access** (ADMIN/MASTER) with fine-grained permissions and a full audit log (who changed what, and when)
- **User management** — email invitations, self-service password change, forgot-password recovery, last-login tracking

## Tech stack

| Category | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA / Hibernate, Spring WebSocket |
| Database | PostgreSQL 16, Flyway (all schema changes go through migrations) |
| Web layer | Thymeleaf (server-side rendering), no frontend framework |
| PDF | openhtmltopdf |
| Label printing | TSPL2, `javax.print`, a small standalone Java client (Print Agent) |
| Infrastructure | Docker / Docker Compose, Caddy (reverse proxy), Tailscale VPN + Funnel |

## Deployment architecture

Production runs on a private network — access only through Tailscale VPN, with no application or database port exposed to the public internet. The one deliberate exception is a narrow public endpoint for scanning the QR code on a calibration label (via Tailscale Funnel), isolated from the rest of the system.

## Getting started (local development)

```bash
# 1. Start the database and MailHog (mail catcher for local dev)
docker compose up -d

# 2. Run the application
./mvnw spring-boot:run
```

Open `http://localhost:8080` — redirects to the login page. Test accounts (seeded automatically on first start):

| Email | Password | Role |
|---|---|---|
| `admin@protaxo.local` | `admin123` | ADMIN |
| `master@protaxo.local` | `master123` | MASTER |

> These are local-development defaults only. The production server uses randomly generated passwords.

## Project structure

Vertical slices by module (not by layer) — each module (`client`, `vehicle`, `invoice`, `catalog`, `security`, `calibration`, `printagent`...) has its own `entity/ dto/ mapper/ repository/ service/ controller/ web/`.

---

<div align="center">Private project. Not for public use.</div>
