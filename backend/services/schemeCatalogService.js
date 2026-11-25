export const CATALOG_METADATA = {
  version: '2026.11',
  lastVerifiedAt: '2026-11-26',
  verificationNote: 'Curated official central and state agricultural portal directory. Verify state-specific cutoffs before filing applications.',
};

export const VERIFIED_SCHEMES = [
  {
    id: 'pm-kisan',
    name: 'PM-KISAN Samman Nidhi',
    category: 'Income Support',
    states: ['All India'],
    description: 'Direct income support of ₹6,000 per year (in three ₹2,000 installments) transferred via DBT to eligible landholding farmer families.',
    benefits: '₹6,000 / year DBT directly into Aadhaar-seeded bank account',
    eligibilityRules: { landOwnershipRequired: true, maxAcres: null, minAcres: 0.01, tenantEligible: false },
    eligibility: ['Landholding farmer family with cultivable land record', 'Valid Aadhaar linked with bank account & e-KYC completed'],
    documents: ['Aadhaar Card', 'Land Record (Khatauni / 7-12 Extract)', 'Bank Passbook (NPCI mapped)'],
    applicationSteps: ['Complete e-KYC at pmkisan.gov.in or local CSC', 'Verify land seeding status with Tehsil/Patwari', 'Track installment status via Aadhaar or registration ID'],
    officialPortal: 'https://pmkisan.gov.in',
    link: 'https://pmkisan.gov.in',
    phone: '155261',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'pmfby',
    name: 'Pradhan Mantri Fasal Bima Yojana (PMFBY)',
    category: 'Crop Insurance',
    states: ['All India'],
    description: 'Comprehensive crop insurance against non-preventable natural risks from pre-sowing to post-harvest losses with subsidized farmer premium.',
    benefits: 'Subsidized farmer premium: 2% Kharif, 1.5% Rabi, 5% commercial/horticulture crops',
    eligibilityRules: { landOwnershipRequired: false, maxAcres: null, minAcres: 0, tenantEligible: true },
    eligibility: ['Owner cultivators, tenant farmers, and sharecroppers growing notified crops', 'Enrollment within seasonal Kharif/Rabi cutoff dates'],
    documents: ['Aadhaar Card', 'Land RoR or Registered Tenancy Agreement', 'Sowing Certificate', 'Bank Account Details'],
    applicationSteps: ['Apply via bank (for KCC loanee) or pmfby.gov.in / CSC before cutoff', 'Select notified crop & insured area', 'Report localized hail/flood loss within 72 hours on Krishi Rakshak helpline 14447'],
    officialPortal: 'https://pmfby.gov.in',
    link: 'https://pmfby.gov.in',
    phone: '14447',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'kcc',
    name: 'Kisan Credit Card (KCC) & Modified Interest Subvention',
    category: 'Credit & Loans',
    states: ['All India'],
    description: 'Timely short-term revolving credit for crop cultivation, post-harvest expenses, animal husbandry, and fisheries.',
    benefits: 'Short-term crop loans with interest subvention for prompt repayment (~4% effective interest on timely repayment up to ₹3 lakh)',
    eligibilityRules: { landOwnershipRequired: false, maxAcres: null, minAcres: 0, tenantEligible: true },
    eligibility: ['Farmers, tenant cultivators, SHGs/JLGs, dairy & poultry farmers'],
    documents: ['Aadhaar & PAN', 'Land documents / Lease deed', 'Passport photos', 'Bank KYC'],
    applicationSteps: ['Submit KCC form at nearest cooperative/commercial bank or CSC', 'Complete scale-of-finance verification by bank manager'],
    officialPortal: 'https://www.myscheme.gov.in/schemes/kcc',
    link: 'https://www.myscheme.gov.in/schemes/kcc',
    phone: '1800-180-1551',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'pm-kusum',
    name: 'PM-KUSUM (Solar Agricultural Pumps & Grid Feeder Solarization)',
    category: 'Irrigation & Solar',
    states: ['All India'],
    description: 'Central and state financial assistance to install standalone solar agriculture pumps (Component B) and solarize grid-connected pumps (Component C).',
    benefits: 'Central financial assistance (~30% standard states, higher in special category states) plus state subsidy on standalone solar pumps',
    eligibilityRules: { landOwnershipRequired: true, maxAcres: null, minAcres: 0.25, tenantEligible: false },
    eligibility: ['Individual farmers, FPOs, cooperatives with agricultural water source and cultivable land'],
    documents: ['Land Record', 'Aadhaar', 'Water source declaration / Existing pump electricity bill'],
    applicationSteps: ['Apply only through your official state renewable energy agency portal linked at pmkusum.mnre.gov.in', 'Deposit beneficiary share only on official verified state portal'],
    officialPortal: 'https://pmkusum.mnre.gov.in',
    link: 'https://pmkusum.mnre.gov.in',
    phone: '1800-180-3333',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'soil-health-card',
    name: 'Soil Health Card & Macro/Micronutrient Advisory',
    category: 'Soil Health',
    states: ['All India'],
    description: 'Periodic laboratory soil testing across 12 macro/micro parameters with crop-wise fertilizer dosage cards to reduce excess chemical cost.',
    benefits: 'Free/subsidized soil nutrient report and balanced NPK + micronutrient dose recommendation',
    eligibilityRules: { landOwnershipRequired: false, maxAcres: null, minAcres: 0, tenantEligible: true },
    eligibility: ['All cultivating farmers across India'],
    documents: ['Aadhaar Card', 'Farm Plot / Khasra Details'],
    applicationSteps: ['Request soil sample collection at local Krishi Vigyan Kendra (KVK) or state agriculture block office', 'Download soil health card on soilhealth.dac.gov.in'],
    officialPortal: 'https://soilhealth.dac.gov.in',
    link: 'https://soilhealth.dac.gov.in',
    phone: '1800-180-1551',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'pmksy-per-drop',
    name: 'PMKSY — Per Drop More Crop (Micro-Irrigation Subsidy)',
    category: 'Irrigation & Solar',
    states: ['All India'],
    description: 'Financial assistance for drip and sprinkler micro-irrigation systems to boost water-use efficiency and yield.',
    benefits: 'Subsidy assistance on approved drip/sprinkler installations (higher % for small & marginal farmers under state norms)',
    eligibilityRules: { landOwnershipRequired: true, maxAcres: null, minAcres: 0.2, tenantEligible: true },
    eligibility: ['Farmers with dependable water source and valid land/lease record; priority for small & marginal farmers (<5 acres)'],
    documents: ['Aadhaar Card', '7/12 or Khatauni Land Extract', 'Bank Passbook', 'Registered Vendor Quotation'],
    applicationSteps: ['Apply via state horticulture/agriculture DBT portal', 'Get pre-inspection clearance before vendor installation'],
    officialPortal: 'https://pmksy.gov.in',
    link: 'https://pmksy.gov.in',
    phone: '1800-180-1551',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'enam',
    name: 'e-NAM (National Agriculture Market)',
    category: 'Market Linkage',
    states: ['All India'],
    description: 'Pan-India electronic trading portal networking regulated APMC mandis for transparent price discovery and assaying.',
    benefits: 'Access multi-state buyers, quality assaying, and online payment directly to farmer bank account',
    eligibilityRules: { landOwnershipRequired: false, maxAcres: null, minAcres: 0, tenantEligible: true },
    eligibility: ['Any farmer selling agricultural produce at e-NAM integrated mandis'],
    documents: ['Aadhaar or Voter ID', 'Bank Account & IFSC'],
    applicationSteps: ['Register on enam.gov.in or at mandi gate kiosk', 'Bring lot for quality assaying and transparent online bidding'],
    officialPortal: 'https://enam.gov.in',
    link: 'https://enam.gov.in',
    phone: '1800-270-0224',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'pkvy',
    name: 'Paramparagat Krishi Vikas Yojana (PKVY)',
    category: 'Organic Farming',
    states: ['All India'],
    description: 'Cluster-based organic farming support providing assistance for organic inputs, PGS-India certification, and residue-free branding.',
    benefits: 'Cluster support for PGS certification, bio-inputs, and organic market linkage',
    eligibilityRules: { landOwnershipRequired: true, maxAcres: 5, minAcres: 0.1, tenantEligible: true },
    eligibility: ['Farmers joining a local organic cluster (typically 20+ hectares group)', 'Max eligible holding cap per cluster guidelines'],
    documents: ['Aadhaar Card', 'Land Holding Record', 'Cluster Membership Consent'],
    applicationSteps: ['Contact district agriculture officer or KVK to join an active PKVY cluster', 'Enroll under pgsindia-ncof.gov.in'],
    officialPortal: 'https://pgsindia-ncof.gov.in',
    link: 'https://pgsindia-ncof.gov.in',
    phone: '1800-180-1551',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'nlm-livestock',
    name: 'National Livestock Mission (NLM) — Entrepreneurship & Breed Improvement',
    category: 'Livestock & Dairy',
    states: ['All India'],
    description: 'Capital subsidy and breed improvement support for rural poultry, goat/sheep farming, piggery, and fodder entrepreneurship.',
    benefits: 'Capital subsidy support via NABARD/bank-linked enterprise projects per NLM operational guidelines',
    eligibilityRules: { landOwnershipRequired: false, maxAcres: null, minAcres: 0, tenantEligible: true },
    eligibility: ['Individual livestock farmers, SHGs, FPOs with valid project DPR and leased/owned shed land'],
    documents: ['Aadhaar & PAN', 'Project DPR', 'Shed/Land proof', 'Training/Experience certificate'],
    applicationSteps: ['Submit application online on udyamimitra.in / NLM state portal', 'Bank credit appraisal followed by State Implementing Agency screening'],
    officialPortal: 'https://nlm.udyamimitra.in',
    link: 'https://nlm.udyamimitra.in',
    phone: '1962',
    lastVerifiedAt: '2026-11-26',
  },
  {
    id: 'mahadbt-agri',
    name: 'MahaDBT / State Farmer Mechanization & Micro-Irrigation Portals',
    category: 'State Subsidy',
    states: ['Maharashtra', 'Gujarat', 'Madhya Pradesh', 'Rajasthan', 'Uttar Pradesh', 'Punjab', 'Haryana', 'Bihar', 'Karnataka', 'Telangana'],
    description: 'Unified state DBT subsidies for tractors, rotavators, farm ponds, polyhouses, and certified seeds (e.g., MahaDBT in Maharashtra, iKhedut in Gujarat).',
    benefits: 'Direct bank subsidy on approved farm machinery, storage sheds, and horticulture structures (rate varies by state & category)',
    eligibilityRules: { landOwnershipRequired: true, maxAcres: null, minAcres: 0.2, tenantEligible: false },
    eligibility: ['Resident farmers of the participating state with active state farmer ID / 7-12 extract'],
    documents: ['State Farmer ID / Aadhaar', 'Land 7-12 / Khatauni', 'Bank Account', 'Caste Certificate (if claiming higher SC/ST slab)'],
    applicationSteps: ['Apply on your state agriculture DBT portal (mahadbt.maharashtra.gov.in, ikhedut.gujarat.gov.in, etc.) via myscheme.gov.in', 'Await lottery selection & upload invoice after pre-sanction'],
    officialPortal: 'https://www.myscheme.gov.in',
    link: 'https://www.myscheme.gov.in',
    phone: '1800-180-1551',
    lastVerifiedAt: '2026-11-26',
  },
];

export function searchSchemesCatalog({ state = '', category = '', query = '' } = {}) {
  const sLower = String(state || '').trim().toLowerCase();
  const cLower = String(category || '').trim().toLowerCase();
  const qLower = String(query || '').trim().toLowerCase();

  return VERIFIED_SCHEMES.filter((scheme) => {
    const matchState =
      !sLower ||
      scheme.states.some((st) => st.toLowerCase() === 'all india' || st.toLowerCase().includes(sLower));
    const matchCat =
      !cLower ||
      cLower === 'all' ||
      scheme.category.toLowerCase().includes(cLower);
    const matchQuery =
      !qLower ||
      scheme.name.toLowerCase().includes(qLower) ||
      scheme.description.toLowerCase().includes(qLower) ||
      scheme.benefits.toLowerCase().includes(qLower) ||
      scheme.category.toLowerCase().includes(qLower);

    return matchState && matchCat && matchQuery;
  });
}

export function evaluateSchemeEligibility({
  state = '',
  farmSize = '',
  landType = 'owned',
  crop = '',
  soilType = '',
  hasLivestock = false,
} = {}) {
  const acres = parseFloat(String(farmSize || '').replace(/[^0-9.]/g, ''));
  const hasAcres = Number.isFinite(acres);
  const landLower = String(landType || 'owned').toLowerCase();
  const isTenant = /tenant|lease|sharecrop|किराए|बटाई/.test(landLower);
  const isIrrigated = /irrigat|canal|drip|bore|सिंचित/.test(landLower);
  const stateNorm = String(state || '').trim();

  return VERIFIED_SCHEMES.map((scheme) => {
    const matchedReasons = [];
    const cautions = [];
    let score = 0;

    // State match
    if (scheme.states.includes('All India')) {
      matchedReasons.push('Available nationwide across all states.');
      score += 2;
    } else if (stateNorm && scheme.states.some((st) => st.toLowerCase().includes(stateNorm.toLowerCase()))) {
      matchedReasons.push(`Active in ${stateNorm}.`);
      score += 3;
    } else {
      cautions.push(`Confirm equivalent state DBT portal for ${stateNorm || 'your state'}.`);
    }

    // Land ownership vs tenancy check
    const rules = scheme.eligibilityRules || {};
    if (isTenant && rules.tenantEligible === false) {
      cautions.push('Usually requires registered land ownership (Patta/7-12) in applicant name.');
      score -= 3;
    } else if (rules.landOwnershipRequired && !isTenant) {
      matchedReasons.push('Matches owned agricultural land criteria.');
      score += 2;
    } else if (rules.tenantEligible) {
      matchedReasons.push('Supports owner cultivators and eligible tenant farmers.');
      score += 1;
    }

    // Farm size check
    if (hasAcres) {
      if (acres <= 5 && ['pm-kisan', 'pmksy-per-drop', 'pkvy', 'kcc'].includes(scheme.id)) {
        matchedReasons.push(`Small/marginal holding (${acres} acres) often qualifies for priority or higher subsidy slab.`);
        score += 2;
      }
      if (rules.minAcres && acres < rules.minAcres) {
        cautions.push(`Check minimum holding guideline (${rules.minAcres} acres).`);
        score -= 1;
      }
    }

    // Crop / practice / livestock bonus relevance
    const cLower = String(crop || '').toLowerCase();
    if (scheme.id === 'pmfby' && cLower) {
      matchedReasons.push(`Covers notified seasonal losses for ${crop}; verify seasonal cutoff.`);
      score += 2;
    }
    if (scheme.id === 'soil-health-card' && soilType) {
      matchedReasons.push(`Provides tailored NPK guidance for ${soilType} soil.`);
      score += 1;
    }
    if ((scheme.id === 'pmksy-per-drop' || scheme.id === 'pm-kusum') && !isIrrigated) {
      matchedReasons.push('Useful if planning micro-irrigation or solar pumping upgrade.');
      score += 1;
    }
    if (scheme.id === 'nlm-livestock' && hasLivestock) {
      matchedReasons.push('Aligned with dairy, goat, sheep, or poultry livelihood support.');
      score += 3;
    }

    let eligibilityStatus = 'Check Portal Guidelines';
    if (cautions.length === 0 && score >= 4) eligibilityStatus = 'Likely Eligible';
    else if (score >= 2) eligibilityStatus = 'Eligible (Verify Documents)';
    else if (cautions.length > 0 && score < 1) eligibilityStatus = 'Likely Ineligible / Land Deed Needed';

    return {
      scheme,
      eligibilityStatus,
      score,
      matchedReasons,
      cautions,
    };
  }).sort((a, b) => b.score - a.score);
}
