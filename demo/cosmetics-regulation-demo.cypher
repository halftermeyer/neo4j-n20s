// ══════════════════════════════════════════════════════════════
// n20s Demo: Cosmetics Regulation Impact Analysis
//
// LPG graph: multi-level formulation BOM with concentration ratios
// RDF knowledge: Turtle properties for ontology + SHACL shapes
//                HAS_TRIPLE nodes for per-ingredient triples (granular scoping)
// n20s: scoped reasoning, forward/backward chaining, SHACL validation
// ══════════════════════════════════════════════════════════════

// ── Step 0: Clean slate ──────────────────────────────────────

MATCH (n) DETACH DELETE n;

// ── Step 1: Create the LPG graph + RDF triples ───────────────

CREATE

// Markets
(eu:Market {name: 'EU', regulation: 'EC 1223/2009'}),
(us:Market {name: 'US', regulation: 'FDA CFR Title 21'}),
(cn:Market {name: 'China', regulation: 'NMPA Cosmetics Regulation'}),
(jp:Market {name: 'Japan', regulation: 'MHLW Standards'}),

// Brands
(luxe:Brand {name: 'Luxe Beauté'}),
(natura:Brand {name: 'Natura Lab'}),
(derma:Brand {name: 'DermaScience'}),

// Suppliers
(supA:Supplier {name: 'ChemSource Asia', country: 'China'}),
(supB:Supplier {name: 'EuroActives GmbH', country: 'Germany'}),
(supC:Supplier {name: 'BioRetinol Inc', country: 'US'}),
(supD:Supplier {name: 'PlantExtract France', country: 'France'}),

// Ingredients — each carries its RDF knowledge as a Turtle property
(retinol:Ingredient {name: 'Retinol', cas: '68-26-8', inci: 'RETINOL',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Retinol rdf:type cosmo:RetinoidAgent , cosmo:PhotosensitiveAgent ;
                  cosmo:maxConcentrationEU "0.05" ;
                  cosmo:regulatoryRef "EC 1223/2009 Annex III/98a" .
  '}),
(retinal:Ingredient {name: 'Retinal', cas: '116-31-4', inci: 'RETINAL',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Retinal rdf:type cosmo:RetinoidAgent , cosmo:PhotosensitiveAgent ;
                  cosmo:maxConcentrationEU "0.01" .
  '}),
(bakuchiol:Ingredient {name: 'Bakuchiol', cas: '10309-37-2', inci: 'BAKUCHIOL',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Bakuchiol rdf:type cosmo:RetinoidAlternative , cosmo:PlantExtract ;
                    cosmo:functionallyEquivalentTo cosmo:RetinoidAgent .
  '}),
(niacinamide:Ingredient {name: 'Niacinamide', cas: '98-92-0', inci: 'NIACINAMIDE',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Niacinamide rdf:type cosmo:VitaminDerivative ;
                      cosmo:optimalPH "6.0" .
  '}),
(ha:Ingredient {name: 'Hyaluronic Acid', cas: '9004-61-9', inci: 'SODIUM HYALURONATE'}),
(squalane:Ingredient {name: 'Squalane', cas: '111-01-3', inci: 'SQUALANE'}),
(vitc:Ingredient {name: 'Ascorbic Acid', cas: '50-81-7', inci: 'ASCORBIC ACID',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:AscorbicAcid rdf:type cosmo:Antioxidant , cosmo:AcidActiveAgent ;
                       cosmo:optimalPH "3.5" .
  '}),
(glycolicAcid:Ingredient {name: 'Glycolic Acid', cas: '79-14-1', inci: 'GLYCOLIC ACID',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:GlycolicAcid rdf:type cosmo:AHAExfoliant , cosmo:AcidActiveAgent ;
                       cosmo:maxConcentrationEU "0.10" .
  '}),
(salicylicAcid:Ingredient {name: 'Salicylic Acid', cas: '69-72-7', inci: 'SALICYLIC ACID',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:SalicylicAcid rdf:type cosmo:BHAExfoliant ;
                        cosmo:maxConcentrationEU "0.02" .
  '}),
(tocopherol:Ingredient {name: 'Tocopherol', cas: '59-02-9', inci: 'TOCOPHEROL',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Tocopherol rdf:type cosmo:Antioxidant ;
                     cosmo:stabilizes cosmo:RetinoidAgent .
  '}),
(caprylic:Ingredient {name: 'Caprylic/Capric Triglyceride', cas: '65381-09-1', inci: 'CAPRYLIC/CAPRIC TRIGLYCERIDE'}),
(water:Ingredient {name: 'Water', cas: '7732-18-5', inci: 'AQUA'}),
(phenoxyethanol:Ingredient {name: 'Phenoxyethanol', cas: '122-99-6', inci: 'PHENOXYETHANOL',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    cosmo:Phenoxyethanol rdf:type cosmo:Preservative ;
                         cosmo:maxConcentrationEU "0.01" .
  '}),

// Substitutions
(bakuchiol)-[:SUBSTITUTE_FOR {reason: 'Plant-based retinoid alternative'}]->(retinol),
(retinal)-[:SUBSTITUTE_FOR {reason: 'Aldehyde form, fewer side effects'}]->(retinol),

// Supply chain
(supA)-[:SUPPLIES {leadTime: 45}]->(retinol),
(supC)-[:SUPPLIES {leadTime: 21}]->(retinol),
(supB)-[:SUPPLIES {leadTime: 14}]->(retinal),
(supD)-[:SUPPLIES {leadTime: 7}]->(bakuchiol),
(supA)-[:SUPPLIES]->(niacinamide),
(supB)-[:SUPPLIES]->(vitc),
(supB)-[:SUPPLIES]->(glycolicAcid),

// ── Product 1: Luxe Anti-Aging Serum (Retinol 0.1%) ─────────

(p1:Product {name: 'Anti-Aging Serum', sku: 'LXB-001'}),
(p1_phA:Phase {name: 'Water Phase'}),
(p1_phB:Phase {name: 'Oil Phase'}),
(p1_phC:Phase {name: 'Active Phase'}),
(p1_premix:PreMix {name: 'Retinol Pre-Mix'}),

(luxe)-[:PRODUCES]->(p1),
(p1)-[:SELLS_IN]->(eu),
(p1)-[:SELLS_IN]->(us),
(p1)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'}),

(p1)-[:CONTAINS {ratio: 0.70}]->(p1_phA),
(p1)-[:CONTAINS {ratio: 0.25}]->(p1_phB),
(p1)-[:CONTAINS {ratio: 0.05}]->(p1_phC),

(p1_phA)-[:CONTAINS {ratio: 0.95}]->(water),
(p1_phA)-[:CONTAINS {ratio: 0.04}]->(niacinamide),
(p1_phA)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol),

(p1_phB)-[:CONTAINS {ratio: 0.20}]->(p1_premix),
(p1_phB)-[:CONTAINS {ratio: 0.80}]->(squalane),

(p1_premix)-[:CONTAINS {ratio: 0.02}]->(retinol),
(p1_premix)-[:CONTAINS {ratio: 0.98}]->(caprylic),

(p1_phC)-[:CONTAINS {ratio: 1.0}]->(ha),

// ── Product 2: Luxe Night Cream (Retinol 0.3%) ──────────────

(p2:Product {name: 'Night Cream', sku: 'LXB-002'}),
(p2_phA:Phase {name: 'Water Phase'}),
(p2_phB:Phase {name: 'Oil Phase'}),
(p2_premix:PreMix {name: 'Retinol Concentrate'}),

(luxe)-[:PRODUCES]->(p2),
(p2)-[:SELLS_IN]->(eu),
(p2)-[:SELLS_IN]->(jp),
(p2)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'}),

(p2)-[:CONTAINS {ratio: 0.60}]->(p2_phA),
(p2)-[:CONTAINS {ratio: 0.40}]->(p2_phB),

(p2_phA)-[:CONTAINS {ratio: 0.97}]->(water),
(p2_phA)-[:CONTAINS {ratio: 0.03}]->(phenoxyethanol),

(p2_phB)-[:CONTAINS {ratio: 0.15}]->(p2_premix),
(p2_phB)-[:CONTAINS {ratio: 0.85}]->(squalane),

(p2_premix)-[:CONTAINS {ratio: 0.05}]->(retinol),
(p2_premix)-[:CONTAINS {ratio: 0.95}]->(caprylic),

// ── Product 3: DermaScience Peel (no retinol) ────────────────

(p3:Product {name: 'Renewal Peel', sku: 'DS-010'}),
(derma)-[:PRODUCES]->(p3),
(p3)-[:SELLS_IN]->(eu),
(p3)-[:SELLS_IN]->(us),

(p3)-[:CONTAINS {ratio: 0.90}]->(water),
(p3)-[:CONTAINS {ratio: 0.08}]->(glycolicAcid),
(p3)-[:CONTAINS {ratio: 0.02}]->(phenoxyethanol),

// ── Product 4: Natura Brightening Serum (Vit C + Niacinamide) ─

(p4:Product {name: 'Brightening Serum', sku: 'NL-005'}),
(natura)-[:PRODUCES]->(p4),
(p4)-[:SELLS_IN]->(eu),
(p4)-[:SELLS_IN]->(cn),

(p4)-[:CONTAINS {ratio: 0.85}]->(water),
(p4)-[:CONTAINS {ratio: 0.10}]->(vitc),
(p4)-[:CONTAINS {ratio: 0.04}]->(niacinamide),
(p4)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol),

// ── Product 5: Natura Retinol Booster (Retinol + Vit C) ──────

(p5:Product {name: 'Retinol Booster', sku: 'NL-008',
  turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    cosmo:RetinolBooster cosmo:containsIngredient cosmo:Retinol , cosmo:AscorbicAcid , cosmo:Tocopherol .
  '}),
(p5_phA:Phase {name: 'Aqueous'}),
(p5_phB:Phase {name: 'Active Oil'}),

(natura)-[:PRODUCES]->(p5),
(p5)-[:SELLS_IN]->(eu),
(p5)-[:SELLS_IN]->(us),

(p5)-[:CONTAINS {ratio: 0.70}]->(p5_phA),
(p5)-[:CONTAINS {ratio: 0.30}]->(p5_phB),

(p5_phA)-[:CONTAINS {ratio: 0.90}]->(water),
(p5_phA)-[:CONTAINS {ratio: 0.10}]->(vitc),

(p5_phB)-[:CONTAINS {ratio: 0.03}]->(retinol),
(p5_phB)-[:CONTAINS {ratio: 0.05}]->(tocopherol),
(p5_phB)-[:CONTAINS {ratio: 0.92}]->(squalane),

// ── Ontology: Turtle property on a dedicated node ────────────
// Class hierarchy + incompatibility rules — clean, human-readable

(:Ontology {name: 'cosmo', turtle: '
  @prefix cosmo: <http://example.org/cosmo#> .
  @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

  cosmo:RetinoidAgent       rdfs:subClassOf cosmo:ActiveIngredient .
  cosmo:RetinoidAlternative rdfs:subClassOf cosmo:ActiveIngredient .
  cosmo:Antioxidant         rdfs:subClassOf cosmo:ActiveIngredient .
  cosmo:AHAExfoliant        rdfs:subClassOf cosmo:Exfoliant .
  cosmo:BHAExfoliant        rdfs:subClassOf cosmo:Exfoliant .
  cosmo:Exfoliant           rdfs:subClassOf cosmo:ActiveIngredient .
  cosmo:PhotosensitiveAgent rdfs:subClassOf cosmo:SensitivityConcern .
  cosmo:AcidActiveAgent     rdfs:subClassOf cosmo:pHSensitiveAgent .
  cosmo:VitaminDerivative   rdfs:subClassOf cosmo:ActiveIngredient .
  cosmo:PlantExtract        rdfs:subClassOf cosmo:NaturalOrigin .
  cosmo:Preservative        rdfs:subClassOf cosmo:FunctionalIngredient .

  cosmo:PhotosensitiveAgent cosmo:incompatibleWith cosmo:AHAExfoliant ;
                            cosmo:incompatibilityRisk "Increased photosensitivity and irritation" ;
                            cosmo:incompatibilitySeverity "high" .

  cosmo:RetinoidAgent       cosmo:incompatibleWith cosmo:AcidActiveAgent ;
                            cosmo:incompatibilityRisk "Retinoid degradation at low pH, increased irritation" ;
                            cosmo:incompatibilitySeverity "medium" .

  cosmo:AcidActiveAgent     cosmo:incompatibleWith cosmo:VitaminDerivative ;
                            cosmo:incompatibilityRisk "Niacin flushing reaction at low pH" ;
                            cosmo:incompatibilitySeverity "low" .
'}),

// ── SHACL Shapes: Turtle property on a dedicated node ────────

(:SHACLRules {name: 'cosmo_validation', turtle: '
  @prefix sh: <http://www.w3.org/ns/shacl#> .
  @prefix cosmo: <http://example.org/cosmo#> .
  @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
  @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
  @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

  cosmo:RetinoidAcidShape a sh:NodeShape ;
    sh:targetClass cosmo:RetinoidAgent ;
    sh:severity sh:Violation ;
    sh:sparql [
      a sh:SPARQLConstraint ;
      sh:message "Retinoid ingredient is incompatible with acid active agent in the same formulation" ;
      sh:select """
        SELECT $this WHERE {
          $this rdf:type/rdfs:subClassOf* cosmo:RetinoidAgent .
          ?formulation cosmo:containsIngredient $this .
          ?formulation cosmo:containsIngredient ?other .
          ?other rdf:type/rdfs:subClassOf* cosmo:AcidActiveAgent .
          FILTER(?other != $this)
        }
      """
    ] .

  cosmo:ActiveIngredientShape a sh:NodeShape ;
    sh:targetClass cosmo:ActiveIngredient ;
    sh:property [
      sh:path cosmo:maxConcentrationEU ;
      sh:minCount 1 ;
      sh:severity sh:Warning ;
      sh:message "Active ingredient missing EU max concentration limit — regulatory gap"
    ] .
'});


// ══════════════════════════════════════════════════════════════
// ── DEMO QUERIES ─────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════


// ── Demo 1: Compute final concentrations via BOM traversal ───
//
// reduce() multiplies ratios along the path:
// Product → Phase (70%) → PreMix (20%) → Retinol (2%) = 0.28%

MATCH path = (p:Product)-[:CONTAINS*]->(i:Ingredient {name: 'Retinol'})
WITH p, i,
     round(reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio) * 100, 4) AS pct
RETURN p.name AS product, pct AS retinol_pct
ORDER BY pct DESC;


// ── Demo 2: Regulation blast radius ──────────────────────────
//
// "EU restricts Retinol above 0.05% — which products are affected?"

MATCH path = (p:Product)-[:CONTAINS*]->(i:Ingredient {name: 'Retinol'})
WITH p, i,
     reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio) AS finalConc
WHERE finalConc > 0.0005
MATCH (p)-[:SELLS_IN]->(m:Market {name: 'EU'})
MATCH (b:Brand)-[:PRODUCES]->(p)
RETURN b.name AS brand, p.name AS product, p.sku AS sku,
       round(finalConc * 100, 4) AS retinol_pct,
       0.05 AS eu_limit_pct,
       'NON-COMPLIANT' AS status
ORDER BY finalConc DESC;


// ── Demo 3: Substitution candidates ──────────────────────────
//
// "What can replace Retinol? Who supplies it?"

MATCH (retinol:Ingredient {name: 'Retinol'})<-[sf:SUBSTITUTE_FOR]-(alt:Ingredient)
OPTIONAL MATCH (sup:Supplier)-[:SUPPLIES]->(alt)
RETURN alt.name AS substitute, alt.inci AS inci,
       sf.reason AS reason,
       collect(sup.name) AS suppliers;


// ══════════════════════════════════════════════════════════════
// FORWARD CHAINING — materialize inferences, then query
// project ingredient Turtle + ontology Turtle → infer → query
// ══════════════════════════════════════════════════════════════

// ── Demo 4a: Project Retinol Booster via Turtle properties ───
//
// Scope ingredients by LPG traversal, add their Turtle to n20s.
// Then add the shared ontology Turtle. Zero Triple nodes needed.

MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(i:Ingredient)
WHERE i.turtle IS NOT NULL
WITH collect(i.turtle) AS ingredientTurtles
MATCH (ont:Ontology {name: 'cosmo'})
WITH ingredientTurtles, ont.turtle AS ontTurtle
// Also get the product's own Turtle (containsIngredient triples)
MATCH (p:Product {name: 'Retinol Booster'})
WHERE p.turtle IS NOT NULL
WITH ingredientTurtles + [p.turtle, ontTurtle] AS allTurtles
UNWIND allTurtles AS t
CALL n20s.graph.addTurtle('forward_check', t)
YIELD graphName, added
RETURN graphName, sum(added) AS totalTriples;

// ── Demo 4b: Forward chaining — materialize all RDFS inferences ─

CALL n20s.graph.infer('forward_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// ── Demo 4c: Query the materialized graph ────────────────────

CALL n20s.graph.query('forward_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?ing1 ?ing2 ?risk ?severity WHERE {
    ?ing1 rdf:type ?class1 .
    ?ing2 rdf:type ?class2 .
    ?class1 cosmo:incompatibleWith ?class2 .
    ?class1 cosmo:incompatibilityRisk ?risk .
    ?class1 cosmo:incompatibilitySeverity ?severity .
    FILTER(?ing1 != ?ing2)
  }
') YIELD row
RETURN row;

// ── Demo 4d: Cleanup forward check ───────────────────────────

CALL n20s.graph.drop('forward_check');


// ══════════════════════════════════════════════════════════════
// BACKWARD CHAINING — reason on-the-fly during query
// project Turtle → query with 'RDFS' (no materialization)
// ══════════════════════════════════════════════════════════════

// ── Demo 5a: Project via Turtle (same scoping as Demo 4a) ────

MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(i:Ingredient)
WHERE i.turtle IS NOT NULL
WITH collect(i.turtle) AS ingredientTurtles
MATCH (ont:Ontology {name: 'cosmo'})
MATCH (p:Product {name: 'Retinol Booster'}) WHERE p.turtle IS NOT NULL
WITH ingredientTurtles + [p.turtle, ont.turtle] AS allTurtles
UNWIND allTurtles AS t
CALL n20s.graph.addTurtle('backward_check', t)
YIELD graphName, added
RETURN graphName, sum(added) AS totalTriples;

// ── Demo 5b: Query with backward chaining — no infer() needed ─

CALL n20s.graph.query('backward_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?ing1 ?ing2 ?risk ?severity WHERE {
    ?ing1 rdf:type ?class1 .
    ?ing2 rdf:type ?class2 .
    ?class1 cosmo:incompatibleWith ?class2 .
    ?class1 cosmo:incompatibilityRisk ?risk .
    ?class1 cosmo:incompatibilitySeverity ?severity .
    FILTER(?ing1 != ?ing2)
  }
', 'RDFS') YIELD row
RETURN row;

// ── Demo 5c: Check EU limits (no reasoning needed) ───────────

CALL n20s.graph.query('backward_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  SELECT ?ingredient ?limit WHERE {
    ?ingredient cosmo:maxConcentrationEU ?limit .
  }
') YIELD row
RETURN row;

// ── Demo 5d: Cleanup backward check ──────────────────────────

CALL n20s.graph.drop('backward_check');


// ══════════════════════════════════════════════════════════════
// SHACL VALIDATION — shapes from Turtle property
// ══════════════════════════════════════════════════════════════

// ── Demo 6a: Project ingredients + ontology + SHACL shapes ───
//
// All three sources from Turtle properties — zero Triple nodes

MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(i:Ingredient)
WHERE i.turtle IS NOT NULL
WITH collect(i.turtle) AS ingredientTurtles
MATCH (ont:Ontology {name: 'cosmo'})
MATCH (sh:SHACLRules {name: 'cosmo_validation'})
MATCH (p:Product {name: 'Retinol Booster'}) WHERE p.turtle IS NOT NULL
WITH ingredientTurtles + [p.turtle, ont.turtle, sh.turtle] AS allTurtles
UNWIND allTurtles AS t
CALL n20s.graph.addTurtle('shacl_check', t)
YIELD graphName, added
RETURN graphName, sum(added) AS totalTriples;

// ── Demo 6b: SHACL Validate ──────────────────────────────────

CALL n20s.graph.validate('shacl_check')
YIELD focusNode, severity, message
RETURN focusNode, severity, message;

// ── Demo 6c: Cleanup SHACL check ─────────────────────────────

CALL n20s.graph.drop('shacl_check');


// ══════════════════════════════════════════════════════════════
// FULL PORTFOLIO RISK SCAN (pure Cypher, no n20s)
// ══════════════════════════════════════════════════════════════

// ── Demo 7: Full portfolio risk scan ─────────────────────────

MATCH (p:Product)-[:SELLS_IN]->(:Market {name: 'EU'})
OPTIONAL MATCH path = (p)-[:CONTAINS*]->(retinol:Ingredient {name: 'Retinol'})
WITH p,
     CASE WHEN path IS NOT NULL
       THEN reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio)
       ELSE 0.0
     END AS retinolConc
MATCH (b:Brand)-[:PRODUCES]->(p)
RETURN b.name AS brand, p.name AS product,
       round(retinolConc * 100, 4) AS retinol_pct,
       CASE
         WHEN retinolConc > 0.0005 THEN 'NON-COMPLIANT'
         WHEN retinolConc > 0 THEN 'COMPLIANT'
         ELSE 'NO RETINOL'
       END AS eu_status
ORDER BY retinolConc DESC;
