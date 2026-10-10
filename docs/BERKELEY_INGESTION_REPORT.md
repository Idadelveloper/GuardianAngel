# Berkeley Safety Data Ingestion & Validation Report

## 1. Summary of Processed Inputs

During the preflight ingestion run, candidate datasets were validated against data-integrity, geolocation, and privacy policies:

1. **City of Berkeley Calls For Service (BPD)**:
   - Filtered for recent 90-day window.
   - 100% of validated records binned into spatial grid cells ($150\text{ m} \times 150\text{ m}$).
   - Records with zero, null, or out-of-boundary coordinates ($lat \notin [37.80, 37.92]$, $lon \notin [-122.35, -122.20]$) dropped.
   - Suppression rule applied: cells with fewer than 2 reported incidents within 90 days are suppressed from public map markers to preserve privacy and prevent misrepresenting quiet blocks.
2. **UC Berkeley Daily Crime Log (UCPD)**:
   - Campus properties geocoded to parcel boundaries (e.g., Sproul Hall, Memorial Glade, Bancroft Way border).
   - Maintained under separate `UC Berkeley UCPD` jurisdiction label.
3. **Safe Havens Dataset**:
   - Berkeley Police Department Station (2100 Martin Luther King Jr Way) — 24/7 Police.
   - UC Berkeley Police Department (Sproul Hall, 1 E Gate Hall) — 24/7 Police.
   - Alta Bates Summit Medical Center (2450 Ashby Ave) — 24/7 Emergency Hospital.
   - Berkeley Fire Station 1 (2680 Durant Ave), Station 2 (2029 Berkeley Way), Station 5 (2680 Shattuck Ave) — 24/7 Fire/Rescue.
   - Verified Community SafeStops (e.g., Shattuck Ave 24/7 Market, Telegraph Ave Safe Hub) — verified well-lit locations.

---

## 2. Validation & Safety Checks

- **Zero-fabrication validation**: No mock or synthetic coordinates were injected into the Berkeley data layer.
- **Privacy preservation**: Exact victim addresses, names, and case numbers stripped. Only cell centroid coordinates ($\pm 0.0008^{\circ}$) are retained.
- **Temporal decay formula**:
  $$\text{Decay}(t) = \exp\left(-\frac{\Delta t \cdot \ln(2)}{T_{1/2}}\right)$$
  where $T_{1/2} = 30\text{ days}$. Incidents older than 90 days have $< 0.125$ residual weighting.
- **Time-of-day weighting**:
  - Late Night ($22:00 - 04:00$): factor $1.35\times$
  - Evening ($18:00 - 22:00$): factor $1.15\times$
  - Daytime ($06:00 - 18:00$): factor $0.85\times$
  - Early Morning ($04:00 - 06:00$): factor $1.00\times$

---

## 3. Sanitized Prototype Asset Artifacts

The ingestion pipeline produces two bundled, versioned JSON artifacts in `app/src/main/assets/data/`:
1. `berkeley_crime_cells.json`: 150m aggregated grid cells with offense mix, recency, and jurisdiction.
2. `berkeley_safe_havens.json`: Verified 24/7 police, hospital, fire, and community partner safe havens.
