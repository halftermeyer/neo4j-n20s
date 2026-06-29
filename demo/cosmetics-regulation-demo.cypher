// ══════════════════════════════════════════════════════════════
// n20s Demo: Cosmetics Regulation Impact Analysis
//
// "EU restricts Retinol above 0.05% — what's the blast radius
//  across our product portfolio, and what can we substitute?"
//
// LPG graph: multi-level formulation BOM with concentration ratios
// RDF knowledge: regulatory ontology + ingredient interactions
// n20s: scoped compliance reasoning + substitution validation
// ══════════════════════════════════════════════════════════════

// ── Step 0: Clean slate ──────────────────────────────────────

MATCH (n) DETACH DELETE n;

// ── Step 1: Build the LPG graph ──────────────────────────────
// Brands, products, formulation phases, pre-mixes, ingredients,
// suppliers, markets — all native LPG

// Markets
CREATE (eu:Market {name: 'EU', regulation: 'EC 1223/2009'})
CREATE (us:Market {name: 'US', regulation: 'FDA CFR Title 21'})
CREATE (cn:Market {name: 'China', regulation: 'NMPA Cosmetics Regulation'})
CREATE (jp:Market {name: 'Japan', regulation: 'MHLW Standards'})

// Brands
CREATE (luxe:Brand {name: 'Luxe Beauté'})
CREATE (natura:Brand {name: 'Natura Lab'})
CREATE (derma:Brand {name: 'DermaScience'})

// Suppliers
CREATE (supA:Supplier {name: 'ChemSource Asia', country: 'China'})
CREATE (supB:Supplier {name: 'EuroActives GmbH', country: 'Germany'})
CREATE (supC:Supplier {name: 'BioRetinol Inc', country: 'US'})
CREATE (supD:Supplier {name: 'PlantExtract France', country: 'France'})

// ── Ingredients ──────────────────────────────────────────────

CREATE (retinol:Ingredient {name: 'Retinol', cas: '68-26-8', inci: 'RETINOL'})
CREATE (retinal:Ingredient {name: 'Retinal', cas: '116-31-4', inci: 'RETINAL'})
CREATE (bakuchiol:Ingredient {name: 'Bakuchiol', cas: '10309-37-2', inci: 'BAKUCHIOL'})
CREATE (niacinamide:Ingredient {name: 'Niacinamide', cas: '98-92-0', inci: 'NIACINAMIDE'})
CREATE (ha:Ingredient {name: 'Hyaluronic Acid', cas: '9004-61-9', inci: 'SODIUM HYALURONATE'})
CREATE (squalane:Ingredient {name: 'Squalane', cas: '111-01-3', inci: 'SQUALANE'})
CREATE (vitc:Ingredient {name: 'Ascorbic Acid', cas: '50-81-7', inci: 'ASCORBIC ACID'})
CREATE (glycolicAcid:Ingredient {name: 'Glycolic Acid', cas: '79-14-1', inci: 'GLYCOLIC ACID'})
CREATE (salicylicAcid:Ingredient {name: 'Salicylic Acid', cas: '69-72-7', inci: 'SALICYLIC ACID'})
CREATE (tocopherol:Ingredient {name: 'Tocopherol', cas: '59-02-9', inci: 'TOCOPHEROL'})
CREATE (caprylic:Ingredient {name: 'Caprylic/Capric Triglyceride', cas: '65381-09-1', inci: 'CAPRYLIC/CAPRIC TRIGLYCERIDE'})
CREATE (water:Ingredient {name: 'Water', cas: '7732-18-5', inci: 'AQUA'})
CREATE (phenoxyethanol:Ingredient {name: 'Phenoxyethanol', cas: '122-99-6', inci: 'PHENOXYETHANOL'})

// Substitution relationships
CREATE (bakuchiol)-[:SUBSTITUTE_FOR {reason: 'Plant-based retinoid alternative'}]->(retinol)
CREATE (retinal)-[:SUBSTITUTE_FOR {reason: 'Aldehyde form, fewer side effects'}]->(retinol)

// Supply chain
CREATE (supA)-[:SUPPLIES {leadTime: 45}]->(retinol)
CREATE (supC)-[:SUPPLIES {leadTime: 21}]->(retinol)
CREATE (supB)-[:SUPPLIES {leadTime: 14}]->(retinal)
CREATE (supD)-[:SUPPLIES {leadTime: 7}]->(bakuchiol)
CREATE (supA)-[:SUPPLIES]->(niacinamide)
CREATE (supB)-[:SUPPLIES]->(vitc)
CREATE (supB)-[:SUPPLIES]->(glycolicAcid)

// ── Product 1: Luxe Anti-Aging Serum ─────────────────────────
// Retinol at 0.1% final concentration (above future EU limit)

CREATE (p1:Product {name: 'Anti-Aging Serum', sku: 'LXB-001'})
CREATE (p1_phA:Phase {name: 'Water Phase'})
CREATE (p1_phB:Phase {name: 'Oil Phase'})
CREATE (p1_phC:Phase {name: 'Active Phase'})
CREATE (p1_premix:PreMix {name: 'Retinol Pre-Mix'})

CREATE (luxe)-[:PRODUCES]->(p1)
CREATE (p1)-[:SELLS_IN]->(eu)
CREATE (p1)-[:SELLS_IN]->(us)
CREATE (p1)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'})

CREATE (p1)-[:CONTAINS {ratio: 0.70}]->(p1_phA)
CREATE (p1)-[:CONTAINS {ratio: 0.25}]->(p1_phB)
CREATE (p1)-[:CONTAINS {ratio: 0.05}]->(p1_phC)

CREATE (p1_phA)-[:CONTAINS {ratio: 0.95}]->(water)
CREATE (p1_phA)-[:CONTAINS {ratio: 0.04}]->(niacinamide)
CREATE (p1_phA)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol)

CREATE (p1_phB)-[:CONTAINS {ratio: 0.20}]->(p1_premix)
CREATE (p1_phB)-[:CONTAINS {ratio: 0.80}]->(squalane)

CREATE (p1_premix)-[:CONTAINS {ratio: 0.02}]->(retinol)
CREATE (p1_premix)-[:CONTAINS {ratio: 0.98}]->(caprylic)

CREATE (p1_phC)-[:CONTAINS {ratio: 1.0}]->(ha)

// ── Product 2: Luxe Night Cream ──────────────────────────────
// Higher retinol concentration (0.3% final)

CREATE (p2:Product {name: 'Night Cream', sku: 'LXB-002'})
CREATE (p2_phA:Phase {name: 'Water Phase'})
CREATE (p2_phB:Phase {name: 'Oil Phase'})
CREATE (p2_premix:PreMix {name: 'Retinol Concentrate'})

CREATE (luxe)-[:PRODUCES]->(p2)
CREATE (p2)-[:SELLS_IN]->(eu)
CREATE (p2)-[:SELLS_IN]->(jp)
CREATE (p2)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'})

CREATE (p2)-[:CONTAINS {ratio: 0.60}]->(p2_phA)
CREATE (p2)-[:CONTAINS {ratio: 0.40}]->(p2_phB)

CREATE (p2_phA)-[:CONTAINS {ratio: 0.97}]->(water)
CREATE (p2_phA)-[:CONTAINS {ratio: 0.03}]->(phenoxyethanol)

CREATE (p2_phB)-[:CONTAINS {ratio: 0.15}]->(p2_premix)
CREATE (p2_phB)-[:CONTAINS {ratio: 0.85}]->(squalane)

CREATE (p2_premix)-[:CONTAINS {ratio: 0.05}]->(retinol)
CREATE (p2_premix)-[:CONTAINS {ratio: 0.95}]->(caprylic)

// ── Product 3: DermaScience Peel ─────────────────────────────
// No retinol, has glycolic acid (AHA)

CREATE (p3:Product {name: 'Renewal Peel', sku: 'DS-010'})
CREATE (derma)-[:PRODUCES]->(p3)
CREATE (p3)-[:SELLS_IN]->(eu)
CREATE (p3)-[:SELLS_IN]->(us)

CREATE (p3)-[:CONTAINS {ratio: 0.90}]->(water)
CREATE (p3)-[:CONTAINS {ratio: 0.08}]->(glycolicAcid)
CREATE (p3)-[:CONTAINS {ratio: 0.02}]->(phenoxyethanol)

// ── Product 4: Natura Brightening Serum ──────────────────────
// Vitamin C + Niacinamide (interaction at low pH)

CREATE (p4:Product {name: 'Brightening Serum', sku: 'NL-005'})
CREATE (natura)-[:PRODUCES]->(p4)
CREATE (p4)-[:SELLS_IN]->(eu)
CREATE (p4)-[:SELLS_IN]->(cn)

CREATE (p4)-[:CONTAINS {ratio: 0.85}]->(water)
CREATE (p4)-[:CONTAINS {ratio: 0.10}]->(vitc)
CREATE (p4)-[:CONTAINS {ratio: 0.04}]->(niacinamide)
CREATE (p4)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol)

// ── Product 5: Natura Retinol Booster ────────────────────────
// Retinol + Vitamin C (known incompatibility)

CREATE (p5:Product {name: 'Retinol Booster', sku: 'NL-008'})
CREATE (p5_phA:Phase {name: 'Aqueous'})
CREATE (p5_phB:Phase {name: 'Active Oil'})

CREATE (natura)-[:PRODUCES]->(p5)
CREATE (p5)-[:SELLS_IN]->(eu)
CREATE (p5)-[:SELLS_IN]->(us)

CREATE (p5)-[:CONTAINS {ratio: 0.70}]->(p5_phA)
CREATE (p5)-[:CONTAINS {ratio: 0.30}]->(p5_phB)

CREATE (p5_phA)-[:CONTAINS {ratio: 0.90}]->(water)
CREATE (p5_phA)-[:CONTAINS {ratio: 0.10}]->(vitc)

CREATE (p5_phB)-[:CONTAINS {ratio: 0.03}]->(retinol)
CREATE (p5_phB)-[:CONTAINS {ratio: 0.05}]->(tocopherol)
CREATE (p5_phB)-[:CONTAINS {ratio: 0.92}]->(squalane);

// ── Step 2: Attach RDF triples ───────────────────────────────

// Ingredient classifications
MATCH (i:Ingredient {name: 'Retinol'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinol', p: 'rdf:type', o: 'cosmo:RetinoidAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinol', p: 'rdf:type', o: 'cosmo:PhotosensitiveAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinol', p: 'cosmo:maxConcentrationEU', o: '"0.05"'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinol', p: 'cosmo:regulatoryRef', o: '"EC 1223/2009 Annex III/98a"'});

MATCH (i:Ingredient {name: 'Retinal'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinal', p: 'rdf:type', o: 'cosmo:RetinoidAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinal', p: 'rdf:type', o: 'cosmo:PhotosensitiveAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Retinal', p: 'cosmo:maxConcentrationEU', o: '"0.01"'});

MATCH (i:Ingredient {name: 'Bakuchiol'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Bakuchiol', p: 'rdf:type', o: 'cosmo:RetinoidAlternative'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Bakuchiol', p: 'rdf:type', o: 'cosmo:PlantExtract'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Bakuchiol', p: 'cosmo:functionallyEquivalentTo', o: 'cosmo:RetinoidAgent'});

MATCH (i:Ingredient {name: 'Ascorbic Acid'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:AscorbicAcid', p: 'rdf:type', o: 'cosmo:Antioxidant'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:AscorbicAcid', p: 'rdf:type', o: 'cosmo:AcidActiveAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:AscorbicAcid', p: 'cosmo:optimalPH', o: '"3.5"'});

MATCH (i:Ingredient {name: 'Glycolic Acid'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:GlycolicAcid', p: 'rdf:type', o: 'cosmo:AHAExfoliant'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:GlycolicAcid', p: 'rdf:type', o: 'cosmo:AcidActiveAgent'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:GlycolicAcid', p: 'cosmo:maxConcentrationEU', o: '"0.10"'});

MATCH (i:Ingredient {name: 'Salicylic Acid'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:SalicylicAcid', p: 'rdf:type', o: 'cosmo:BHAExfoliant'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:SalicylicAcid', p: 'cosmo:maxConcentrationEU', o: '"0.02"'});

MATCH (i:Ingredient {name: 'Niacinamide'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Niacinamide', p: 'rdf:type', o: 'cosmo:VitaminDerivative'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Niacinamide', p: 'cosmo:optimalPH', o: '"6.0"'});

MATCH (i:Ingredient {name: 'Phenoxyethanol'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Phenoxyethanol', p: 'rdf:type', o: 'cosmo:Preservative'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Phenoxyethanol', p: 'cosmo:maxConcentrationEU', o: '"0.01"'});

MATCH (i:Ingredient {name: 'Tocopherol'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Tocopherol', p: 'rdf:type', o: 'cosmo:Antioxidant'})
CREATE (i)-[:HAS_TRIPLE]->(:Triple {s: 'cosmo:Tocopherol', p: 'cosmo:stabilizes', o: 'cosmo:RetinoidAgent'});

// ── Step 3: Ontology triples (shared rules) ──────────────────

// Class hierarchy
CREATE (:Triple:Ontology {s: 'cosmo:RetinoidAgent', p: 'rdfs:subClassOf', o: 'cosmo:ActiveIngredient'})
CREATE (:Triple:Ontology {s: 'cosmo:RetinoidAlternative', p: 'rdfs:subClassOf', o: 'cosmo:ActiveIngredient'})
CREATE (:Triple:Ontology {s: 'cosmo:Antioxidant', p: 'rdfs:subClassOf', o: 'cosmo:ActiveIngredient'})
CREATE (:Triple:Ontology {s: 'cosmo:AHAExfoliant', p: 'rdfs:subClassOf', o: 'cosmo:Exfoliant'})
CREATE (:Triple:Ontology {s: 'cosmo:BHAExfoliant', p: 'rdfs:subClassOf', o: 'cosmo:Exfoliant'})
CREATE (:Triple:Ontology {s: 'cosmo:Exfoliant', p: 'rdfs:subClassOf', o: 'cosmo:ActiveIngredient'})
CREATE (:Triple:Ontology {s: 'cosmo:PhotosensitiveAgent', p: 'rdfs:subClassOf', o: 'cosmo:SensitivityConcern'})
CREATE (:Triple:Ontology {s: 'cosmo:AcidActiveAgent', p: 'rdfs:subClassOf', o: 'cosmo:pHSensitiveAgent'})
CREATE (:Triple:Ontology {s: 'cosmo:VitaminDerivative', p: 'rdfs:subClassOf', o: 'cosmo:ActiveIngredient'})
CREATE (:Triple:Ontology {s: 'cosmo:PlantExtract', p: 'rdfs:subClassOf', o: 'cosmo:NaturalOrigin'})
CREATE (:Triple:Ontology {s: 'cosmo:Preservative', p: 'rdfs:subClassOf', o: 'cosmo:FunctionalIngredient'})

// Incompatibility rules
CREATE (:Triple:Ontology {s: 'cosmo:PhotosensitiveAgent', p: 'cosmo:incompatibleWith', o: 'cosmo:AHAExfoliant'})
CREATE (:Triple:Ontology {s: 'cosmo:PhotosensitiveAgent', p: 'cosmo:incompatibilityRisk', o: '"Increased photosensitivity and irritation"'})
CREATE (:Triple:Ontology {s: 'cosmo:PhotosensitiveAgent', p: 'cosmo:incompatibilitySeverity', o: '"high"'})

CREATE (:Triple:Ontology {s: 'cosmo:RetinoidAgent', p: 'cosmo:incompatibleWith', o: 'cosmo:AcidActiveAgent'})
CREATE (:Triple:Ontology {s: 'cosmo:RetinoidAgent', p: 'cosmo:incompatibilityRisk', o: '"Retinoid degradation at low pH, increased irritation"'})
CREATE (:Triple:Ontology {s: 'cosmo:RetinoidAgent', p: 'cosmo:incompatibilitySeverity', o: '"medium"'})

CREATE (:Triple:Ontology {s: 'cosmo:AcidActiveAgent', p: 'cosmo:incompatibleWith', o: 'cosmo:VitaminDerivative'})
CREATE (:Triple:Ontology {s: 'cosmo:AcidActiveAgent', p: 'cosmo:incompatibilityRisk', o: '"Niacin flushing reaction at low pH"'})
CREATE (:Triple:Ontology {s: 'cosmo:AcidActiveAgent', p: 'cosmo:incompatibilitySeverity', o: '"low"'});


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
WHERE finalConc > 0.0005  // 0.05% limit
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

MATCH (retinol:Ingredient {name: 'Retinol'})<-[:SUBSTITUTE_FOR]-(alt:Ingredient)
OPTIONAL MATCH (sup:Supplier)-[:SUPPLIES]->(alt)
RETURN alt.name AS substitute, alt.inci AS inci,
       collect(sup.name) AS suppliers,
       [(alt)-[:SUBSTITUTE_FOR]->(retinol) |
        head([(alt)-[sf:SUBSTITUTE_FOR]->(retinol) | sf.reason])][0] AS reason;


// ── Demo 4: Validate substitute with n20s ────────────────────
//
// "Is Bakuchiol compliant in EU? Any interactions with other
//  ingredients in the Anti-Aging Serum?"

// Scope: Bakuchiol triples + all ingredients from the target product + ontology
CALL {
  MATCH (:Ingredient {name: 'Bakuchiol'})-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (:Product {name: 'Anti-Aging Serum'})-[:CONTAINS*]->(:Ingredient)-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (t:Triple:Ontology)
  RETURN t.s AS s, t.p AS p, t.o AS o
}
WITH n20s.graph.project('reformulation_check', s, p, o) AS g
RETURN g.graphName AS graph, g.tripleCount AS triples;

// Infer
CALL n20s.graph.infer('reformulation_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// Check: any incompatibilities between Bakuchiol and existing ingredients?
CALL n20s.graph.query('reformulation_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?ing1 ?ing2 ?class1 ?class2 ?risk ?severity WHERE {
    ?ing1 rdf:type ?class1 .
    ?ing2 rdf:type ?class2 .
    ?class1 cosmo:incompatibleWith ?class2 .
    ?class1 cosmo:incompatibilityRisk ?risk .
    ?class1 cosmo:incompatibilitySeverity ?severity .
    FILTER(?ing1 != ?ing2)
  }
') YIELD row
RETURN row;

// Check: EU concentration limits for the substitute
CALL n20s.graph.query('reformulation_check', '
  PREFIX cosmo: <http://example.org/cosmo#>

  SELECT ?ingredient ?limit WHERE {
    ?ingredient cosmo:maxConcentrationEU ?limit .
  }
') YIELD row
RETURN row;

CALL n20s.graph.drop('reformulation_check');


// ── Demo 5: Product 5 — known incompatibility ───────────────
//
// Retinol Booster has Retinol + Vitamin C (Ascorbic Acid)
// Retinoids are incompatible with acid actives

CALL {
  MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(:Ingredient)-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (t:Triple:Ontology)
  RETURN t.s AS s, t.p AS p, t.o AS o
}
WITH n20s.graph.project('booster_check', s, p, o) AS g
RETURN g.graphName, g.tripleCount;

CALL n20s.graph.infer('booster_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// Detect incompatibilities
CALL n20s.graph.query('booster_check', '
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

CALL n20s.graph.drop('booster_check');


// ── Demo 6: Full portfolio risk scan ─────────────────────────
//
// For each product sold in EU: compute Retinol concentration,
// check against limit, flag non-compliant

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
