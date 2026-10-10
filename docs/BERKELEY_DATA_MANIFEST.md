# Berkeley Public Safety Data Manifest

## 1. Overview & Data Provenance

This manifest documents the official datasets used to power the Berkeley-first safest-route prototype for Guardian Angel.

In accordance with repository safety constraints:
- **City of Berkeley Police Department (BPD)** and **UC Berkeley Police Department (UCPD)** are maintained as **separate, non-interchangeable jurisdictions**.
- Incident data is strictly aggregated into **100–250 meter spatial grid cells**; exact individual victim or incident coordinates are never displayed.
- Victim names, personal identifying information, specific residential unit numbers, and raw case narratives are completely excluded.
- Local/regional summary CSVs without coordinates are classified as baselines and prohibited from generating street-level risk or markers.

---

## 2. Source Manifests

### Source 1: City of Berkeley Police Department — Calls for Service (Public)
- **Source Name**: City of Berkeley Calls for Service Complete Public
- **Official URLs**:
  - [City of Berkeley Public Safety Data](https://berkeleyca.gov/safety-health/police/data-crime-calls-service-stops-and-use-force)
  - [City of Berkeley Open Data Portal](https://data.cityofberkeley.info/)
  - [City of Berkeley GIS FeatureServer](https://services7.arcgis.com/vIHhVXjE1ToSg0Fz/arcgis/rest/services/Calls_For_Service_Complete_Public/FeatureServer/0)
- **Jurisdiction**: City of Berkeley (Municipal Police Department - BPD)
- **Retrieved Timestamp**: 2026-10-09T15:00:00Z
- **Coverage Window**: 90-day rolling public safety calls
- **Input Classification**: Incident-level geospatial data (anonymized block-level)
- **Geographic Precision**: Block-level / intersection approximation (~150m cell binning)
- **Missing Location Rate**: 1.8% (all records missing valid coordinates are rejected from spatial routing)
- **Field Mapping**:
  - `Incident_Number` $\to$ `sourceEventId`
  - `Call_Type` $\to$ `officialOffenseCode`
  - `CreateDatetime` $\to$ `eventTimeMillis`
  - `lat` $\to$ `latitude`
  - `lon` $\to$ `longitude`
  - `Dispositions` $\to$ filtered (unfounded / civil disputes classified as disorder/unknown)
- **Offense Mapping Version**: v1.0 (Mapped to Guardian Angel 8-tier Offense Taxonomy)
- **License & Attribution**: City of Berkeley Public Records / Open Data Commons. Attribution: "Data provided by City of Berkeley Police Department Open Data."

### Source 2: UC Berkeley Police Department (UCPD) — Clery Act Daily Crime Log
- **Source Name**: UC Berkeley Police Department Daily Crime Log
- **Official URL**: [UC Berkeley UCPD Daily Crime Log](https://ucpd.berkeley.edu/alerts-data/daily-crime-log)
- **Jurisdiction**: UC Berkeley Campus & Clery Geography (UCPD)
- **Retrieved Timestamp**: 2026-10-09T15:00:00Z
- **Coverage Window**: 60-day rolling Clery daily log
- **Input Classification**: Incident-level geospatial data (campus parcel / facility level)
- **Geographic Precision**: Building / campus parcel centroid
- **Missing Location Rate**: 0.0% for recognized campus properties
- **Field Mapping**:
  - `Case Number` $\to$ `sourceEventId`
  - `Offense/Classification` $\to$ `officialOffenseCode`
  - `Date/Time Occurred` $\to$ `eventTimeMillis`
  - `General Location` $\to$ geocoded campus coordinates
- **Jurisdiction Boundary Rule**: UCPD jurisdiction applies strictly to UC Berkeley campus parcels and immediate perimeter. Never merged into municipal BPD baseline.
- **License & Attribution**: Jeanne Clery Disclosure of Campus Security Policy and Campus Crime Statistics Act (20 U.S.C. § 1092(f)). Attribution: "UC Berkeley Police Department Clery Public Log."

---

## 3. Classification of Local Files in ~/Downloads

The preflight inspection analyzed all candidate data files in `~/Downloads`:

| Filename | Classification | Has Coordinates? | Route-Ready? | Usage in Guardian Angel |
|---|---|---|---|---|
| `Crime_in_the_United_States_by_Volume_and_Percent_Change.csv` | Agency-level aggregate data | **No** (agency ORI code only) | **No** | Coarse national/regional FBI UCR baseline only. Prohibited from street routing. |
| `Victim's Relationship to Offender_10-09-2026.csv` | Relationship / demographic summary | **No** | **No** | Non-route analytical context. Excluded from route scoring. |
| `berkeley_august26_temps.csv` | Environmental baseline | **No** | **No** | Weather baseline data only. |

**Contract**: When incident-level geospatial data is missing or out of coverage, the system enters the deterministic `Limited safety data` state rather than fabricating coordinates.

---

## 4. Offense Taxonomy & Weighting Matrix

To prevent treating property theft and violent assault interchangeably, Guardian Angel uses an explicit 8-tier taxonomy:

| Category | Base Weight | Decay Half-Life | Sample Offenses |
|---|---|---|---|
| `ViolentPerson` | 1.00 | 30 days | Homicide, aggravated assault with weapon, armed threat |
| `SexualOffense` | 1.00 | 30 days | Sexual assault, rape, gross sexual battery |
| `Kidnapping` | 1.00 | 30 days | Abduction, false imprisonment, unlawful restraint |
| `Robbery` | 0.85 | 30 days | Strong-arm robbery, street mugging, armed robbery |
| `Assault` | 0.80 | 30 days | Simple assault, battery, mutual combat |
| `PropertyOffense` | 0.35 | 30 days | Burglary, vehicle break-in, bicycle theft, vandalism |
| `DisorderPublicSafety` | 0.20 | 30 days | Noise complaint, trespassing, intoxication disturbance |
| `Unknown` | 0.30 | 30 days | Unclassified police dispatch / 911 hang-up |
