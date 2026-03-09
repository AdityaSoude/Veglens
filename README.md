# 🌱 VegLens — AI-powered Food Transparency

**VegLens**, or as my roommates call it *“Mini Me”*, is an AI-powered app that helps users instantly identify whether a food item is **Veg, Non-Veg, or Vegan** — bringing back the clarity of India’s familiar green/red dot system to places where it doesn’t exist.

---

## 🧩 Problem Statement

When I moved from India to the U.S., I noticed something simple but frustrating — **there’s no green or red dot** on food packages to indicate whether a product is vegetarian or not.

Reading ingredient labels turned into a puzzle of **scientific names, E-codes, and vague terms like “natural flavors” or “enzymes.”**  
Even food apps and barcode scanners gave inconsistent or incomplete answers.

**Goal:**  
To **eliminate ambiguity** in food labeling and help people — especially travelers, vegetarians, and vegans — make confident and transparent food choices.

---
## 📸 Screenshots
![WhatsApp Image 2025-11-09 at 9 39 49 PM (2)](https://github.com/user-attachments/assets/07ac9a92-378a-43f6-9e45-162dd0321b87)

![WhatsApp Image 2025-11-09 at 9 46 53 PM](https://github.com/user-attachments/assets/ba44ccaf-9bcb-445c-9074-1f24f24a949f)

![WhatsApp Image 2025-11-09 at 9 39 49 PM (1)](https://github.com/user-attachments/assets/8a613de3-3e06-486b-86b9-7bd14c9ea22a)

## 🚀 Features

- 🔍 **Scan barcodes** to fetch ingredient details.
- ✍️ **Manually enter ingredients** to check if they’re Veg, Non-Veg, or Vegan.
- 🧠 **AI-powered reasoning** using **Google Vertex AI** to classify ambiguous ingredients.
- 🧾 **Three-step fallback mechanism**:
  1. Check local ingredient knowledge base (deterministic)
  2. Query **Open Food Facts API** for missing details
  3. Use **Vertex AI** for contextual classification
- ⚙️ **Confidence scoring** for ambiguous ingredients.
- 👩‍🍳 **User dietary preferences** — Vegetarian / Vegan / Strict mode.
- 🌍 **Transparent results** with reasoning and confidence levels.
- 🔴🟢 Optional **visual indicator system** (Veg / Non-Veg / Ambiguous).

---

## 🧠 Architecture Overview

**Backend:** Spring Boot (Java 17)  
**Frontend:** React + Vite  
**AI Layer:** Google Vertex AI  
**External Data:** Open Food Facts API  
**Local Data:** Custom `ingredients-kb.json` knowledge base  

### Backend Services
- **IngredientKB** → Fast lookups and synonym matching (e.g., “gelatine” = “E441”)  
- **InfoService** → Core classification logic  
- **VertexAIService** → Calls Vertex AI model for contextual reasoning  
- **ProductDetailsService** → Integrates data from Open Food Facts  
- **PolicyOptions** → Handles dietary modes (Vegetarian / Vegan / Strict)

---

## ⚙️ Setup

### Prerequisites
- Java 17  
- Node.js & npm  
- Google Cloud account with Vertex AI enabled  
- (Optional) ngrok for public API testing

### Clone the repo
```bash
git clone https://github.com/AdityaSoude/Veglens.git
cd Veglens






