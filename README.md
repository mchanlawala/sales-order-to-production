# Production Order Scheduler – Full Stack

**PODISE (Production Order Design & Intelligent Scheduling Engine)**  
End‑to‑end solution for converting sales orders into scheduled production orders – with real‑time constraint‑based scheduling, AI insights, and a modern Angular dashboard.

---

## 🚀 Overview

PODISE helps solar (and other discrete) manufacturers automatically:

- Accept sales orders (Excel or SAP)
- Split quantities by fixed lot size and work centre batch limits
- Schedule operations backward/forward respecting shifts, breaks, downtime
- Generate production orders with operation‑level timings and component reservations
- Visualise results via dashboard, Gantt charts, and detailed tables
- Optionally get AI‑powered insights (Llama 3.2)

**Architecture**: Spring Boot backend + Angular frontend + PostgreSQL + Kafka + optional Ollama.

---

## 📦 Repository Structure



https://github.com/mchanlawala/sales-order-to-production.git


backend> mvn clean spring-boot:run

sales-order-to-production> ollama serve
frontend> npm install
frontend> ng serve