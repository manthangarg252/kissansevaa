package com.kissansevaai.core.data

import com.kissansevaai.core.model.LoanProduct
import com.kissansevaai.core.model.Scheme

object CatalogData {

    val SCHEMES: List<Scheme> = listOf(
        Scheme(
            id = "1",
            name = "PM-KISAN (Pradhan Mantri Kisan Samman Nidhi)",
            category = "Income Support",
            description = "Provides income support of ₹6,000 per year in three equal installments directly to farmer families across India through Direct Benefit Transfer.",
            eligibility = listOf("All landholding farmer families", "Subject to exclusion criteria for higher income groups"),
            link = "https://pmkisan.gov.in/"
        ),
        Scheme(
            id = "2",
            name = "Pradhan Mantri Fasal Bima Yojana (PMFBY)",
            category = "Insurance",
            description = "Comprehensive crop insurance scheme providing financial support to farmers suffering crop loss/damage due to natural calamities, pests, and diseases at very low premium rates.",
            eligibility = listOf("All farmers growing notified crops", "Loanee and Non-Loanee farmers", "Sharecroppers and tenant farmers"),
            link = "https://pmfby.gov.in/"
        ),
        Scheme(
            id = "3",
            name = "Pradhan Mantri Krishi Sinchai Yojana (PMKSY)",
            category = "Irrigation",
            description = "Per Drop More Crop — enhances water use efficiency at farm level through Micro Irrigation technologies like drip and sprinkler irrigation with subsidy support.",
            eligibility = listOf("All farmer categories", "Land holding with access to water source", "Drip/Sprinkler irrigation adoption"),
            link = "https://pmksy.gov.in/"
        ),
        Scheme(
            id = "4",
            name = "Paramparagat Krishi Vikas Yojana (PKVY)",
            category = "Organic",
            description = "Promotes organic farming through cluster approach and PGS certification. Provides ₹50,000/ha for 3 years for inputs, certification, and marketing.",
            eligibility = listOf("Farmers in a cluster of 20+ hectares", "Willingness to adopt organic practices for 3 years"),
            link = "https://pgsindia-ncof.gov.in/"
        ),
        Scheme(
            id = "5",
            name = "Kisan Credit Card (KCC) Scheme",
            category = "Financial",
            description = "Provides timely credit support from banking system for cultivation, post-harvest, and consumption needs at subsidized interest rates (4% effective after subvention).",
            eligibility = listOf("All farmers — owner cultivators", "Tenant farmers, oral lessees & share croppers", "SHGs and Joint Liability Groups"),
            link = "https://www.pmjdy.gov.in/scheme"
        ),
        Scheme(
            id = "6",
            name = "Soil Health Card Scheme",
            category = "Soil Health",
            description = "Provides soil health cards to farmers with crop-wise recommendations for nutrients and fertilizers to improve soil productivity and reduce input costs.",
            eligibility = listOf("All farmers across India", "Free soil testing through government labs"),
            link = "https://soilhealth.dac.gov.in/"
        ),
        Scheme(
            id = "7",
            name = "Rashtriya Krishi Vikas Yojana (RKVY-RAFTAAR)",
            category = "Technology",
            description = "Provides flexibility to states to plan and execute agriculture development programs including agri-startups with focus on pre & post harvest infrastructure.",
            eligibility = listOf("State government nominated projects", "Agri-entrepreneurs and startups", "Farmer Producer Organizations"),
            link = "https://rkvy.nic.in/"
        ),
        Scheme(
            id = "8",
            name = "National Food Security Mission (NFSM)",
            category = "Seeds",
            description = "Increases production of rice, wheat, pulses, coarse cereals through area expansion, productivity enhancement, and soil fertility restoration.",
            eligibility = listOf("Farmers in identified districts", "Growing rice, wheat, pulses, coarse cereals", "Priority to small & marginal farmers"),
            link = "https://nfsm.gov.in/"
        ),
        Scheme(
            id = "9",
            name = "National Mission for Sustainable Agriculture (NMSA)",
            category = "Technology",
            description = "Promotes sustainable agriculture through climate change adaptation strategies, water use efficiency, and soil health management.",
            eligibility = listOf("Farmers in rainfed areas", "Adopting climate-resilient practices", "All categories of farmers"),
            link = "https://nmsa.dac.gov.in/"
        ),
        Scheme(
            id = "10",
            name = "Mission for Integrated Development of Horticulture (MIDH)",
            category = "Horticulture",
            description = "Holistic development of horticulture sector covering fruits, vegetables, spices, flowers, coconut, cashew, and bamboo with 40-55% subsidy.",
            eligibility = listOf("Farmers growing horticultural crops", "Available through National Horticulture Board", "Subsidy up to 40-55% on infrastructure"),
            link = "https://midh.gov.in/"
        ),
        Scheme(
            id = "11",
            name = "PM Kisan SAMPADA Yojana",
            category = "Infrastructure",
            description = "Creates modern food processing infrastructure, reduces wastage, boosts farmer income through Mega Food Parks and cold chain units.",
            eligibility = listOf("Individual entrepreneurs", "Cooperatives and SHGs", "FPOs and local bodies"),
            link = "https://mofpi.gov.in/Schemes/pm-kisan-sampada-yojana"
        ),
        Scheme(
            id = "12",
            name = "eNAM (Electronic National Agriculture Market)",
            category = "Marketing",
            description = "Pan-India electronic trading portal connecting APMC mandis to create a unified national market for agricultural commodities.",
            eligibility = listOf("All farmers with produce to sell", "Registered traders and commission agents", "Available in 1000+ mandis across India"),
            link = "https://enam.gov.in/"
        ),
        Scheme(
            id = "13",
            name = "Agriculture Infrastructure Fund (AIF)",
            category = "Infrastructure",
            description = "Medium to long-term debt financing facility of ₹1 lakh crore for post-harvest management and community farming assets with 3% interest subvention.",
            eligibility = listOf("Farmers and FPOs", "PACS, Marketing Cooperative Societies", "Agri-entrepreneurs and startups"),
            link = "https://agriinfra.dac.gov.in/"
        ),
        Scheme(
            id = "14",
            name = "Gramin Bhandaran Yojana (Rural Godown Scheme)",
            category = "Infrastructure",
            description = "Creates scientific storage capacity in rural areas with subsidy support (25-33%) to reduce post-harvest losses and prevent distress sale.",
            eligibility = listOf("Individual farmers and groups", "Cooperatives and SHGs", "NGOs and companies"),
            link = "https://www.nabard.org/"
        ),
        Scheme(
            id = "15",
            name = "Sub-Mission on Agroforestry (SMAF)",
            category = "Organic",
            description = "Encourages tree plantation on farm lands alongside crops for additional income from timber, fruits, and medicinal plants.",
            eligibility = listOf("All farmers willing to plant trees", "Minimum 0.5 hectare farm land", "Priority for rain-fed areas"),
            link = "https://agricoop.nic.in/"
        ),
        Scheme(
            id = "16",
            name = "Agri-Clinics and Agri-Business Centres (ACABC)",
            category = "Technology",
            description = "Provides agri-graduates with subsidized loans (36-44% subsidy) and training to set up Agri-Clinics for expert farming advisory services.",
            eligibility = listOf("Agriculture graduates", "Graduates with biological sciences degree", "Training at MANAGE-approved institutes"),
            link = "https://www.acabcmis.gov.in/"
        )
    )

    val LOAN_PRODUCTS: List<LoanProduct> = listOf(
        LoanProduct(
            id = "l1",
            name = "Kisan Credit Card (KCC)",
            type = "Govt",
            description = "Provides timely credit support for cultivation, post-harvest needs, and consumption at a subsidized 4% interest rate (after subvention).",
            benefits = listOf("Interest subvention up to 3%", "Flexible repayment", "Insurance coverage under PMFBY", "Covers crop, animal husbandry & fisheries"),
            eligibility = listOf("Owner cultivator farmers", "Tenant farmers & sharecroppers", "SHGs and Joint Liability Groups"),
            link = "https://www.sbi.co.in/web/agri-rural/agriculture-banking/kisan-credit-card",
            provider = "Nationalized & Rural Banks",
            interestRate = "4% p.a. (subvention applied)",
            maxAmount = "₹3,00,000",
            tenure = "Renewable annually"
        ),
        LoanProduct(
            id = "l2",
            name = "NABARD Dairy Entrepreneurship Scheme",
            type = "Bank",
            description = "Financial assistance for setting up modern dairy farms, vermicompost units, and milk processing with 25-33% capital subsidy.",
            benefits = listOf("Capital subsidy 25-33%", "Longer tenure up to 7 years", "Technical guidance from NDDB", "No collateral up to ₹1.6 lakh"),
            eligibility = listOf("Individual farmers and groups", "NGOs and cooperatives", "Entrepreneurs with dairy farm plan"),
            link = "https://www.nabard.org/",
            provider = "NABARD / Scheduled Commercial Banks",
            interestRate = "7.5% - 9.0% p.a.",
            maxAmount = "₹25,00,000",
            tenure = "Up to 7 years"
        ),
        LoanProduct(
            id = "l3",
            name = "MUDRA Shishu / Kishor / Tarun Loan",
            type = "Govt",
            description = "Collateral-free loans up to ₹10 lakh for agri-allied activities, food processing, and rural micro-enterprises.",
            benefits = listOf("No collateral required", "Shishu (up to ₹50K) at lowest rates", "Kishor (₹50K-₹5L) for expansion", "Tarun (₹5L-₹10L) for established units"),
            eligibility = listOf("Small and micro enterprises", "Agri-allied and food processing units", "Individual entrepreneurs"),
            link = "https://www.mudra.org.in/",
            provider = "MUDRA / Partner Banks",
            interestRate = "8.0% - 11.5% p.a.",
            maxAmount = "₹10,00,000",
            tenure = "3 to 5 years"
        ),
        LoanProduct(
            id = "l4",
            name = "SBI Crop Loan",
            type = "Bank",
            description = "Short-term production credit for seasonal agricultural operations including purchase of seeds, fertilizers, pesticides, and labour.",
            benefits = listOf("Interest rate from 7% p.a.", "Additional 3% subvention for timely repayment", "Repayment linked to harvest season", "Covers all Kharif & Rabi crops"),
            eligibility = listOf("Land owners with clear title", "Tenant farmers with lease agreement", "Minimum 1 acre cultivable land"),
            link = "https://www.sbi.co.in/web/agri-rural/agriculture-banking/crop-loan",
            provider = "State Bank of India",
            interestRate = "7.0% p.a.",
            maxAmount = "Based on scale of finance",
            tenure = "Up to 12 months"
        ),
        LoanProduct(
            id = "l5",
            name = "Tractor & Farm Equipment Loan",
            type = "Bank",
            description = "Financing for purchasing new tractors, harvesters, tillers, and modern farm machinery with extended tenure.",
            benefits = listOf("Up to 90% financing", "Tenure up to 9 years", "Low processing fees", "Doorstep verification in rural areas"),
            eligibility = listOf("2+ acres of agricultural land", "Stable agricultural income", "Valid land ownership documents"),
            link = "https://www.mahindrafinance.com/tractor-loans",
            provider = "Mahindra Finance / Commercial Banks",
            interestRate = "9.5% - 12.0% p.a.",
            maxAmount = "Up to 90% of on-road cost",
            tenure = "Up to 7 years"
        ),
        LoanProduct(
            id = "l6",
            name = "Warehouse Receipt Financing (e-NWR)",
            type = "Bank",
            description = "Short-term loans pledged against stored agricultural produce in WDRA-registered warehouses, preventing distress sales.",
            benefits = listOf("Enables selling when mandi prices rise", "Lower interest rates vs unsecured loans", "Avoids middlemen exploitation", "Loan up to 70% of produce value"),
            eligibility = listOf("Produce stored in registered warehouse", "Valid Negotiable Warehouse Receipt (NWR)", "Commodity meeting WDRA standards"),
            link = "https://wdra.gov.in/",
            provider = "Commercial & Regional Rural Banks",
            interestRate = "7.5% - 9.0% p.a.",
            maxAmount = "Up to 70% of commodity value",
            tenure = "Up to 12 months"
        ),
        LoanProduct(
            id = "l7",
            name = "Agri Gold Loan",
            type = "Bank",
            description = "Instant disbursement credit facility specifically tailored for agricultural requirements against gold ornament pledge.",
            benefits = listOf("Same-day loan disbursement", "Minimal documentation", "Lower agri-specific interest rate", "Flexible bullet or EMI repayment"),
            eligibility = listOf("Farmers possessing gold jewellery", "Agricultural activity proof/land record", "Age 18+ years"),
            link = "https://www.hdfcbank.com/personal/borrow/popular-loans/gold-loan",
            provider = "HDFC Bank / Public Sector Banks",
            interestRate = "7.25% - 8.5% p.a.",
            maxAmount = "₹25,00,000",
            tenure = "6 to 36 months"
        )
    )
}
