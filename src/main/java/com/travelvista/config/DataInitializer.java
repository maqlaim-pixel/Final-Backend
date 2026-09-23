package com.travelvista.config;

import com.travelvista.model.*;
import com.travelvista.repository.*;
import com.travelvista.repository.DestinationRepository;
import com.travelvista.repository.HotelRepository;
import com.travelvista.repository.ActivityRepository;
import com.travelvista.repository.BlogRepository;
import com.travelvista.repository.MenuRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {
    @org.springframework.beans.factory.annotation.Value("${BOOTSTRAP_SUPER_ADMIN_EMAIL:}")
    private String bootstrapSuperAdminEmail;
    @org.springframework.beans.factory.annotation.Value("${BOOTSTRAP_SUPER_ADMIN_PASSWORD:}")
    private String bootstrapSuperAdminPassword;
    @org.springframework.beans.factory.annotation.Value("${BOOTSTRAP_EDITOR_EMAIL:}")
    private String bootstrapEditorEmail;
    @org.springframework.beans.factory.annotation.Value("${BOOTSTRAP_EDITOR_PASSWORD:}")
    private String bootstrapEditorPassword;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PackageRepository packageRepository;
    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;
    private final BlogRepository blogRepository;
    private final TestimonialRepository testimonialRepository;
    private final FAQRepository faqRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final MenuRepository menuRepository;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                           PackageRepository packageRepository, DestinationRepository destinationRepository,
                           HotelRepository hotelRepository, ActivityRepository activityRepository,
                           BlogRepository blogRepository, TestimonialRepository testimonialRepository,
                           FAQRepository faqRepository, SiteSettingRepository siteSettingRepository,
                           PasswordEncoder passwordEncoder, MenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.packageRepository = packageRepository;
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
        this.blogRepository = blogRepository;
        this.testimonialRepository = testimonialRepository;
        this.faqRepository = faqRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.menuRepository = menuRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println("🌱 Checking seed data...");

        // ── Roles ─────────────────────────────────────────────────────
        Role superAdmin = getOrCreateRole("super_admin", "Full access to all modules");
        Role editor = getOrCreateRole("editor", "Can create and publish content");
        Role contributor = getOrCreateRole("contributor", "Can create draft content only");
        Role sales = getOrCreateRole("sales", "Leads and enquiries management");
        Role customer = getOrCreateRole("customer", "Regular website user");

        // ── Optional environment-configured staff accounts ────────────
        // Credentials are never hardcoded or printed. A configured account's ROLE and
        // active/verified state are reconciled so an existing row can never silently keep
        // a non-staff role; its password is never rewritten.
        seedStaffUser(bootstrapSuperAdminEmail, bootstrapSuperAdminPassword,
                "TravelVista Super Admin", superAdmin);
        seedStaffUser(bootstrapEditorEmail, bootstrapEditorPassword,
                "TravelVista Editor", editor);

        // ── Packages ──────────────────────────────────────────────────
        if (packageRepository.countAll() == 0) {
            seedPackages();
            System.out.println("  ✅ 10 sample packages seeded");
        }

        // ── Testimonials ──────────────────────────────────────────────
        if (testimonialRepository.count() == 0) {
            seedTestimonials();
            System.out.println("  ✅ 8 testimonials seeded");
        }

        // ── FAQs ──────────────────────────────────────────────────────
        if (faqRepository.count() == 0) {
            seedFAQs();
            System.out.println("  ✅ 6 FAQs seeded");
        }

        // ── Destinations ─────────────────────────────────────────────
        if (destinationRepository.count() == 0) {
            seedDestinations();
            System.out.println("  ✅ 10 destinations seeded");
        }

        // ── Hotels ───────────────────────────────────────────────────
        if (hotelRepository.count() == 0) {
            seedHotels();
            System.out.println("  ✅ 6 hotels seeded");
        }

        // ── Activities ───────────────────────────────────────────────
        if (activityRepository.count() == 0) {
            seedActivities();
            System.out.println("  ✅ 6 activities seeded");
        }

        // ── Blogs ────────────────────────────────────────────────────
        if (blogRepository.count() == 0) {
            seedBlogs();
            System.out.println("  ✅ 4 blogs seeded");
        }

        // ── Site Settings ─────────────────────────────────────────────
        if (siteSettingRepository.count() == 0) {
            seedSettings();
            System.out.println("  ✅ 11 site settings seeded");
        }

        // ── Menus ──────────────────────────────────────────────────────
        if (menuRepository.count() == 0) {
            seedMenus();
            System.out.println("  ✅ 7 main menus seeded with destinations");
        }

        System.out.println("🎉 Seed data ready!\n");
    }

    private void seedStaffUser(String configuredEmail, String configuredPassword,
                               String displayName, Role role) {
        if (configuredEmail == null || configuredEmail.isBlank()
                || configuredPassword == null || configuredPassword.isBlank()) {
            return;
        }

        String email = configuredEmail.trim().toLowerCase(java.util.Locale.ROOT);
        Optional<User> existingAccount = userRepository.findByEmailIgnoreCase(email);

        if (existingAccount.isPresent()) {
            User existing = existingAccount.get();
            String currentRole = existing.getRole() == null ? "none" : existing.getRole().getName();
            boolean changed = false;

            if (!role.getName().equalsIgnoreCase(currentRole)) {
                existing.setRole(role);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                existing.setIsActive(true);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existing.getEmailVerified())) {
                existing.setEmailVerified(true);
                changed = true;
            }
            if (changed) {
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                userRepository.save(existing);
                System.out.println("  Bootstrap staff account corrected to role " + role.getName()
                        + " (previous role: " + currentRole + ")");
            }
            return;
        }

        User user = new User(displayName, email,
                passwordEncoder.encode(configuredPassword), role);
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        userRepository.save(user);
        System.out.println("  Staff account created from environment configuration: " + role.getName());
    }

    private Role getOrCreateRole(String name, String desc) {
        Optional<Role> existing = roleRepository.findByName(name);
        if (existing.isPresent()) return existing.get();
        Role role = new Role(name, desc);
        return roleRepository.save(role);
    }

    private void seedPackages() {
        Object[][] packages = {
            {"Rajasthan Heritage Tour", "rajasthan-heritage-tour",
             "Embark on a magical 6-day journey through the royal state of Rajasthan.",
             "Explore Jaipur, Jodhpur & Udaipur with heritage stays and desert safari.",
             "Rajasthan", "Rajasthan", "India", 6, 5, 14999.00, "domestic",
             "Heritage,Culture,Family,Luxury", true, 4.8, 342},
            {"Kerala Backwater & Beaches", "kerala-backwater-beaches",
             "Discover God's Own Country with backwaters, hills, and beaches.",
             "Houseboat cruise, tea plantation trek, and beach relaxation.",
             "Kerala", "Kerala", "India", 5, 4, 12499.00, "domestic",
             "Nature,Family,Honeymoon", true, 4.7, 289},
            {"Swiss Alps Adventure", "swiss-alps-adventure",
             "Experience the breathtaking beauty of Switzerland on this premium adventure.",
             "Glacier Express, Jungfraujoch, and Lake Lucerne.",
             "Switzerland", null, "Switzerland", 7, 6, 185000.00, "international",
             "Adventure,Luxury,Nature", true, 4.9, 198},
            {"Goa Beach Holiday", "goa-beach-holiday",
             "Escape to India's beach paradise with this getaway.",
             "4 days of beaches, nightlife, Portuguese heritage, and water sports.",
             "Goa", "Goa", "India", 4, 3, 8999.00, "domestic",
             "Beach,Nightlife,Family", false, 4.5, 456},
            {"Thailand Tropical Escape", "thailand-tropical-escape",
             "Dive into the magic of Thailand.",
             "Bangkok temples, floating markets, and Phuket beaches.",
             "Thailand", null, "Thailand", 6, 5, 45000.00, "international",
             "Beach,Culture,Adventure", true, 4.6, 267},
            {"Ladakh Road Trip", "ladakh-road-trip",
             "Conquer the highest motorable passes.",
             "8-day Manali to Leh through the world's highest passes.",
             "Ladakh", "Jammu & Kashmir", "India", 8, 7, 22999.00, "domestic",
             "Adventure,Road Trip,Photography", true, 4.9, 178},
            {"Japan Cultural Odyssey", "japan-cultural-odyssey",
             "Immerse yourself in the land of the rising sun.",
             "Tokyo, Kyoto, Mount Fuji — the ultimate Japan experience.",
             "Japan", null, "Japan", 10, 9, 245000.00, "international",
             "Culture,Luxury,Food", false, 4.8, 156},
            {"Andaman Island Paradise", "andaman-island-paradise",
             "Escape to the pristine Andaman Islands.",
             "Havelock Island beaches, snorkeling, and colonial history.",
             "Andaman", null, "India", 5, 4, 18999.00, "domestic",
             "Beach,Adventure,Honeymoon", false, 4.7, 203},
            {"Dubai Luxury Experience", "dubai-luxury-experience",
             "Experience the height of luxury in Dubai.",
             "5-star Dubai with desert safari, Burj Khalifa, and fine dining.",
             "Dubai", null, "UAE", 5, 4, 85000.00, "international",
             "Luxury,Shopping,Family", false, 4.6, 312},
            {"Manali Shimla Hill Station", "manali-shimla-hill-station",
             "Escape the heat with this refreshing hill station tour.",
             "Shimla & Manali with adventure activities and scenic drives.",
             "Manali", "Himachal Pradesh", "India", 5, 4, 11999.00, "domestic",
             "Hill Station,Family,Adventure", false, 4.4, 389},
        };

        for (Object[] p : packages) {
            TravelPackage pkg = new TravelPackage();
            pkg.setTitle((String) p[0]);
            pkg.setSlug((String) p[1]);
            pkg.setDescription((String) p[2]);
            pkg.setShortDescription((String) p[3]);
            pkg.setDestination((String) p[4]);
            pkg.setState((String) p[5]);
            pkg.setCountry((String) p[6]);
            pkg.setDurationDays((Integer) p[7]);
            pkg.setDurationNights((Integer) p[8]);
            pkg.setStartingPrice(new BigDecimal(((Number) p[9]).doubleValue()));
            pkg.setCategory((String) p[10]);
            pkg.setTags((String) p[11]);
            pkg.setFeatured((Boolean) p[12]);
            pkg.setRating(new BigDecimal(((Number) p[13]).doubleValue()));
            pkg.setReviewCount((Integer) p[14]);
            pkg.setStatus("published");
            packageRepository.save(pkg);
        }
    }

    private void seedTestimonials() {
        String[][] testimonials = {
            {"Priya Sharma", "Mumbai", "The Rajasthan Heritage Tour was absolutely magical! Every hotel was perfectly curated.", "5.0"},
            {"Rahul Mehta", "Delhi", "Kerala backwaters on the houseboat was the highlight of our honeymoon!", "5.0"},
            {"Sarah Johnson", "London, UK", "Our Swiss Alps trip was a dream come true. The Glacier Express was breathtaking.", "4.9"},
            {"Amit Patel", "Bangalore", "The Ladakh road trip was the adventure of a lifetime. Pangong was surreal.", "5.0"},
            {"Ananya Reddy", "Hyderabad", "Thailand trip was amazing value. The floating markets exceeded expectations.", "4.8"},
            {"Vikram Singh", "Jaipur", "Japan trip was perfect blend of tradition and modernity. Unforgettable!", "4.9"},
            {"Meera Iyer", "Chennai", "Andaman Islands were paradise! Crystal-clear waters and excellent snorkeling.", "4.7"},
            {"Karthik Nair", "Kochi", "Dubai luxury was worth every penny. Burj Khalifa at sunset was amazing!", "4.8"},
        };

        for (String[] t : testimonials) {
            Testimonial testimonial = new Testimonial();
            testimonial.setName(t[0]);
            testimonial.setLocation(t[1]);
            testimonial.setQuote(t[2]);
            testimonial.setRating(new BigDecimal(t[3]));
            testimonial.setStatus("published");
            testimonialRepository.save(testimonial);
        }
    }

    private void seedFAQs() {
        String[][] faqs = {
            {"How do I book a package?", "Click Enquire Now on any package page, call us, or use WhatsApp. Our experts will get in touch within 24 hours.", "1"},
            {"What payment methods do you accept?", "We accept UPI, bank transfers, credit/debit cards, and EMI. A 30% advance is required to confirm.", "2"},
            {"Can I customize my itinerary?", "Absolutely! All packages are fully customizable. Tell us your preferences.", "3"},
            {"Do you offer group discounts?", "Yes, special rates for 8+ travelers. Contact our team for custom group pricing.", "4"},
            {"What is your cancellation policy?", "30+ days: full refund. 15-29 days: 50%. Less than 15 days: no refund.", "5"},
            {"Do you arrange visa support?", "Yes, complete visa assistance including documentation and appointment scheduling.", "6"},
        };

        for (String[] f : faqs) {
            FAQ faq = new FAQ();
            faq.setEntityType("general");
            faq.setQuestion(f[0]);
            faq.setAnswer(f[1]);
            faq.setSortOrder(Integer.parseInt(f[2]));
            faq.setStatus("published");
            faqRepository.save(faq);
        }
    }

    private void seedDestinations() {
        Object[][] data = {
            {"Rajasthan", "rajasthan", "India", "Rajasthan", "domestic", "Land of Kings — majestic forts, palaces, and the Thar Desert.", "Explore Jaipur, Jodhpur & Udaipur with heritage stays.", "https://images.unsplash.com/photo-1477587458883-47145ed94245?w=600", "Land of Kings", "Oct-Mar", "22°C", "Hindi, Rajasthani, English", 24},
            {"Kerala", "kerala", "India", "Kerala", "domestic", "God's Own Country — backwaters, hills, and beaches.", "Houseboat cruise and tea plantation trek.", "https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?w=600", "God's Own Country", "Sep-Mar", "25°C", "Malayalam, English", 18},
            {"Goa", "goa", "India", "Goa", "domestic", "India's beach paradise with nightlife and heritage.", "Beaches, nightlife, and Portuguese heritage.", "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?w=600", "Beach Paradise", "Nov-Feb", "28°C", "Konkani, English", 15},
            {"Ladakh", "ladakh", "India", "Jammu & Kashmir", "domestic", "Roof of the World — highest passes and stunning landscapes.", "Pangong Lake and Nubra Valley.", "https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?w=600", "Roof of the World", "Jun-Sep", "10°C", "Ladakhi, Hindi", 12},
            {"Switzerland", "switzerland", "Switzerland", null, "international", "Heaven on Earth — Alps, lakes, and chocolate.", "Glacier Express and Jungfraujoch.", "https://images.unsplash.com/photo-1530122037265-a5f1f91d3b99?w=600", "Heaven on Earth", "Dec-Mar, Jun-Sep", "-5°C to 20°C", "German, French, English", 12},
            {"Thailand", "thailand", "Thailand", null, "international", "Land of Smiles — temples, beaches, and food.", "Bangkok temples and Phuket beaches.", "https://images.unsplash.com/photo-1528181304800-259b08848526?w=600", "Land of Smiles", "Nov-Feb", "28°C", "Thai, English", 20},
            {"Dubai", "dubai", "UAE", null, "international", "City of Gold — luxury and modern architecture.", "Burj Khalifa and desert safari.", "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?w=600", "City of Gold", "Nov-Mar", "25°C", "Arabic, English", 16},
            {"Japan", "japan", "Japan", null, "international", "Land of the Rising Sun — tradition meets modernity.", "Tokyo, Kyoto, and Mount Fuji.", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600", "Land of the Rising Sun", "Mar-May, Oct-Nov", "15°C", "Japanese, English", 10},
        };
        for (Object[] d : data) {
            Destination dest = new Destination();
            dest.setName((String) d[0]);
            dest.setSlug((String) d[1]);
            dest.setCountry((String) d[2]);
            dest.setState((String) d[3]);
            dest.setType((String) d[4]);
            dest.setDescription((String) d[5]);
            dest.setShortDescription((String) d[6]);
            dest.setImage((String) d[7]);
            dest.setTagline((String) d[8]);
            dest.setBestTime((String) d[9]);
            dest.setAvgTemp((String) d[10]);
            dest.setLanguages((String) d[11]);
            dest.setPackageCount((Integer) d[12]);
            dest.setStatus("published");
            destinationRepository.save(dest);
        }

        // ═══ RANN OF KUTCH — Rich CMS Demo Destination ═══
        Destination rannOfKutch = new Destination();
        rannOfKutch.setName("Rann of Kutch");
        rannOfKutch.setSlug("rann-of-kutch");
        rannOfKutch.setCountry("India");
        rannOfKutch.setState("Gujarat");
        rannOfKutch.setType("domestic");
        rannOfKutch.setDescription("Rann of Kutch is a vast area of seasonal salt marshes in the Thar Desert in the Kutch district of Gujarat, India. Famous for its white salt desert, cultural festivals, and traditional handicrafts.");
        rannOfKutch.setShortDescription("The White Desert of India — a mesmerizing landscape of white salt stretching to the horizon.");
        rannOfKutch.setImage("https://images.unsplash.com/photo-1590050752117-29885e590d8e?w=600");
        rannOfKutch.setTagline("The White Desert of India");
        rannOfKutch.setBestTime("October to March");
        rannOfKutch.setAvgTemp("25°C");
        rannOfKutch.setLanguages("Gujarati, Hindi, English");
        rannOfKutch.setPackageCount(0);
        rannOfKutch.setStatus("published");
        rannOfKutch.setFeatured(true);

        // Hero CMS data
        rannOfKutch.setHeroTitle("Rann of Kutch");
        rannOfKutch.setHeroSubtitle("Experience the endless white salt desert of Gujarat — a landscape unlike anywhere else on Earth.");
        rannOfKutch.setHeroCtaText("Explore Packages");
        rannOfKutch.setHeroCtaUrl("/packages?destination=Rann+of+Kutch");
        rannOfKutch.setHeroImages(
            "https://images.unsplash.com/photo-1590050752117-29885e590d8e?w=1400&h=600&fit=crop,"
          + "https://images.unsplash.com/photo-1506461883276-594a12b11cf3?w=1400&h=600&fit=crop,"
          + "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?w=1400&h=600&fit=crop"
        );

        // About CMS data
        rannOfKutch.setAboutTitle("About Rann of Kutch");
        rannOfKutch.setAboutContent(
            "The Rann of Kutch is one of the largest salt deserts in the world, spanning nearly 10,000 square kilometers in the Kutch district of Gujarat, India. "
          + "This extraordinary landscape transforms from a vast white salt marsh during winter to a shallow wetland during the monsoon. "
          + "The White Rann, a stunning expanse of white salt crystals, stretches as far as the eye can see, creating an almost otherworldly landscape. "
          + "The region is home to the vibrant Kutchi culture, known for its rich traditions of handicrafts, textile arts, and folk music. "
          + "Every winter, the Rann Utsav festival brings this desert to life with cultural performances, traditional cuisine, and artisanal crafts. "
          + "From the black hill of Kala Dungar to the pristine beaches of Mandvi, the Rann of Kutch offers an unforgettable journey through India's most unique terrain."
        );
        rannOfKutch.setAboutImage("https://images.unsplash.com/photo-1590050752117-29885e590d8e?w=800&h=500&fit=crop");
        rannOfKutch.setAboutImagePosition("right");

        // Attractions CMS data
        rannOfKutch.setAttractions(
            "["
          + "{\"title\":\"White Rann\",\"image\":\"https://images.unsplash.com/photo-1590050752117-29885e590d8e?w=400&h=300&fit=crop\",\"description\":\"The breathtaking white salt desert that stretches to the horizon — a natural wonder unique to this region.\",\"location\":\"Dhordo\",\"displayOrder\":1,\"isActive\":true},"
          + "{\"title\":\"Kala Dungar\",\"image\":\"https://images.unsplash.com/photo-1506461883276-594a12b11cf3?w=400&h=300&fit=crop\",\"description\":\"The highest point in Kutch — a black hill with panoramic views of the Great Rann and ancient temples.\",\"location\":\"Kutch\",\"displayOrder\":2,\"isActive\":true},"
          + "{\"title\":\"Mandvi Beach\",\"image\":\"https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?w=400&h=300&fit=crop\",\"description\":\"Pristine golden sand beach with calm waters — perfect for water sports and relaxation.\",\"location\":\"Mandvi\",\"displayOrder\":3,\"isActive\":true},"
          + "{\"title\":\"Dhordo Village\",\"image\":\"https://images.unsplash.com/photo-1477587458883-47145ed94245?w=400&h=300&fit=crop\",\"description\":\"The gateway to the White Rann — a traditional village that hosts the Rann Utsav festival.\",\"location\":\"Dhordo\",\"displayOrder\":4,\"isActive\":true},"
          + "{\"title\":\"Kutch Museum\",\"image\":\"https://images.unsplash.com/photo-1548013146-72479768bada?w=400&h=300&fit=crop\",\"description\":\"Gujarat's oldest museum showcasing Kutchi heritage, tribal art, and archaeological artifacts.\",\"location\":\"Bhuj\",\"displayOrder\":5,\"isActive\":true},"
          + "{\"title\":\"Dholavira\",\"image\":\"https://images.unsplash.com/photo-1524492412937-b28074a5d7da?w=400&h=300&fit=crop\",\"description\":\"UNESCO World Heritage Site — one of the five largest Harappan settlements with advanced water management.\",\"location\":\"Kutch\",\"displayOrder\":6,\"isActive\":true}"
          + "]"
        );

        // Experiences CMS data
        rannOfKutch.setExperiences(
            "["
          + "{\"title\":\"Rann Utsav Festival\",\"image\":\"https://images.unsplash.com/photo-1590050752117-29885e590d8e?w=400&h=300&fit=crop\",\"description\":\"Annual cultural festival with folk dances, music, handicrafts, and authentic Kutchi cuisine under the stars.\",\"category\":\"Cultural\",\"price\":null,\"duration\":\"Nov - Feb\",\"location\":\"Dhordo\",\"displayOrder\":1,\"isActive\":true},"
          + "{\"title\":\"Kutchi Cuisine Experience\",\"image\":\"https://images.unsplash.com/photo-1506461883276-594a12b11cf3?w=400&h=300&fit=crop\",\"description\":\"Savor traditional Kutchi thali, dabeli, kutchi kulfi, and authentic local dishes with a royal feast experience.\",\"category\":\"Food\",\"price\":\"₹800/person\",\"duration\":\"2-3 hours\",\"location\":\"Bhuj\",\"displayOrder\":2,\"isActive\":true},"
          + "{\"title\":\"Traditional Handicrafts\",\"image\":\"https://images.unsplash.com/photo-1514222134-b57cbb8ce073?w=400&h=300&fit=crop\",\"description\":\"Explore Ajrakh block printing, Bandhani tie-dye, and Kutchi embroidery workshops with local artisans.\",\"category\":\"Craft\",\"price\":\"₹500/person\",\"duration\":\"3-4 hours\",\"location\":\"Bhuj\",\"displayOrder\":3,\"isActive\":true},"
          + "{\"title\":\"Camel Safari in White Rann\",\"image\":\"https://images.unsplash.com/photo-1477587458883-47145ed94245?w=400&h=300&fit=crop\",\"description\":\"Ride camels through the mesmerizing white salt desert at sunset — an unforgettable experience.\",\"category\":\"Adventure\",\"price\":\"₹1200/person\",\"duration\":\"2 hours\",\"location\":\"White Rann\",\"displayOrder\":4,\"isActive\":true},"
          + "{\"title\":\"Bhuj City Heritage Walk\",\"image\":\"https://images.unsplash.com/photo-1548013146-72479768bada?w=400&h=300&fit=crop\",\"description\":\"Explore Aina Mahal, Prag Mahal, and the bustling local markets of Bhuj.\",\"category\":\"Heritage\",\"price\":\"₹300/person\",\"duration\":\"Half Day\",\"location\":\"Bhuj\",\"displayOrder\":5,\"isActive\":true}"
          + "]"
        );

        // Highlights CMS data
        rannOfKutch.setDestinationHighlights(
            "["
          + "{\"title\":\"Best Time: October to March\",\"description\":\"The weather is pleasant and the Rann Utsav festival is in full swing.\",\"icon\":\"calendar\",\"displayOrder\":1,\"isActive\":true},"
          + "{\"title\":\"UNESCO World Heritage Site\",\"description\":\"Dholavira — an ancient Indus Valley Civilization site.\",\"icon\":\"award\",\"displayOrder\":2,\"isActive\":true},"
          + "{\"title\":\"White Salt Desert\",\"description\":\"One of the largest salt deserts in the world — a natural wonder.\",\"icon\":\"mountain\",\"displayOrder\":3,\"isActive\":true},"
          + "{\"title\":\"Rich Handicrafts\",\"description\":\"Famous for Ajrakh, Bandhani, and Kutchi embroidery.\",\"icon\":\"sparkles\",\"displayOrder\":4,\"isActive\":true},"
          + "{\"title\":\"Cultural Festivals\",\"description\":\"Rann Utsav brings folk dance, music, and cuisine together.\",\"icon\":\"music\",\"displayOrder\":5,\"isActive\":true},"
          + "{\"title\":\"Photographer's Paradise\",\"description\":\"Endless landscapes and vibrant culture for stunning shots.\",\"icon\":\"camera\",\"displayOrder\":6,\"isActive\":true}"
          + "]"
        );

        // Quick Info CMS data
        rannOfKutch.setQuickInfo(
            "["
          + "{\"label\":\"Best Time\",\"value\":\"October to March\",\"icon\":\"calendar\",\"displayOrder\":1},"
          + "{\"label\":\"Ideal Duration\",\"value\":\"3-5 Days\",\"icon\":\"clock\",\"displayOrder\":2},"
          + "{\"label\":\"Weather\",\"value\":\"20°C to 30°C\",\"icon\":\"thermometer\",\"displayOrder\":3},"
          + "{\"label\":\"How to Reach\",\"value\":\"Bhuj Airport (BHU) — 80 km\",\"icon\":\"plane\",\"displayOrder\":4},"
          + "{\"label\":\"Nearest Railway\",\"value\":\"Bhuj Railway Station\",\"icon\":\"train\",\"displayOrder\":5},"
          + "{\"label\":\"Language\",\"value\":\"Gujarati, Hindi, English\",\"icon\":\"languages\",\"displayOrder\":6},"
          + "{\"label\":\"Famous For\",\"value\":\"White Desert, Rann Utsav, Handicrafts\",\"icon\":\"star\",\"displayOrder\":7},"
          + "{\"label\":\"Local Transport\",\"value\":\"Jeep, Camel Cart, Auto\",\"icon\":\"car\",\"displayOrder\":8}"
          + "]"
        );

        // Package coming soon
        rannOfKutch.setPackagesComingSoon(true);
        rannOfKutch.setPackagesComingSoonText("Curated travel packages for Rann of Kutch are coming soon. Contact us for a custom itinerary.");
        rannOfKutch.setShowPackages(true);

        // SEO
        rannOfKutch.setSeoTitle("Rann of Kutch, Gujarat — The White Desert of India | TravelVista");
        rannOfKutch.setSeoDescription("Explore the Rann of Kutch — Gujarat's mesmerizing white salt desert. Experience Rann Utsav, camel safaris, Kutchi cuisine, and rich handicrafts.");

        destinationRepository.save(rannOfKutch);
    }

    private void seedHotels() {
        Object[][] data = {
            {"The Grand Palace Resort", "grand-palace-resort", "Jaipur, Rajasthan", "Jaipur", "India", "A luxurious heritage resort in the Pink City.", "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=600", "Luxury", 4.8, 8500.00, 342},
            {"Kumarakom Lake Resort", "kumarakom-lake-resort", "Kumarakom, Kerala", "Kumarakom", "India", "Serene lakeside resort in God's Own Country.", "https://images.unsplash.com/photo-1582719508461-905c673771fd?w=600", "Heritage", 4.7, 12000.00, 289},
            {"The Leela Goa", "leela-goa", "Cavelossim, Goa", "Goa", "India", "Premium beach resort in South Goa.", "https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=600", "Beach", 4.9, 15000.00, 198},
            {"Taj Lake Palace", "taj-lake-palace", "Udaipur, Rajasthan", "Udaipur", "India", "Iconic floating palace on Lake Pichola.", "https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=600", "Luxury", 4.9, 35000.00, 312},
            {"Wildflower Hall", "wildflower-hall", "Shimla, Himachal Pradesh", "Shimla", "India", "Mountain luxury resort in the Himalayas.", "https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=600", "Mountain", 4.8, 18000.00, 178},
            {"Svarna Vilas", "svarna-vilas", "Udaipur, Rajasthan", "Udaipur", "India", "Boutique heritage hotel in Udaipur.", "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=600", "Boutique", 4.6, 6500.00, 203},
        };
        for (Object[] h : data) {
            Hotel hotel = new Hotel();
            hotel.setName((String) h[0]);
            hotel.setSlug((String) h[1]);
            hotel.setLocation((String) h[2]);
            hotel.setCity((String) h[3]);
            hotel.setCountry((String) h[4]);
            hotel.setDescription((String) h[5]);
            hotel.setImage((String) h[6]);
            hotel.setCategory((String) h[7]);
            hotel.setRating(new BigDecimal(((Number) h[8]).doubleValue()));
            hotel.setPricePerNight(new BigDecimal(((Number) h[9]).doubleValue()));
            hotel.setReviewCount((Integer) h[10]);
            hotel.setStatus("published");
            hotelRepository.save(hotel);
        }
    }

    private void seedActivities() {
        Object[][] data = {
            {"Paragliding in Bir Billing", "paragliding-bir-billing", "Himachal Pradesh", "Adventure", "Fly over the Dhauladhar range.", "https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d?w=600", "1 Day", "Moderate", 3500.00, 4.9, 245},
            {"Scuba Diving in Andaman", "scuba-diving-andaman", "Havelock Island", "Water Sports", "Dive into crystal-clear waters.", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600", "Half Day", "Beginner", 6500.00, 4.8, 189},
            {"Desert Safari in Jaisalmer", "desert-safari-jaisalmer", "Rajasthan", "Adventure", "Camel safari in the Thar Desert.", "https://images.unsplash.com/photo-1509316785289-025f5b846b35?w=600", "1 Day", "Easy", 2500.00, 4.7, 342},
            {"Trek to Kedarkantha", "kedarkantha-trek", "Uttarakhand", "Trekking", "Summit trek with stunning Himalayan views.", "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=600", "6 Days", "Moderate", 8999.00, 4.9, 156},
            {"White Water Rafting", "white-water-rafting", "Rishikesh", "Adventure", "Thrilling rapids on the Ganges.", "https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?w=600", "Half Day", "Moderate", 1800.00, 4.6, 289},
            {"Wildlife Safari", "wildlife-safari-jim-corbett", "Jim Corbett", "Wildlife", "Tiger safari in India's oldest national park.", "https://images.unsplash.com/photo-1535338454528-1b0e6dd7e257?w=600", "2 Days", "Easy", 7500.00, 4.8, 203},
        };
        for (Object[] a : data) {
            Activity act = new Activity();
            act.setName((String) a[0]);
            act.setSlug((String) a[1]);
            act.setLocation((String) a[2]);
            act.setCategory((String) a[3]);
            act.setDescription((String) a[4]);
            act.setImage((String) a[5]);
            act.setDuration((String) a[6]);
            act.setDifficulty((String) a[7]);
            act.setPrice(new BigDecimal(((Number) a[8]).doubleValue()));
            act.setRating(new BigDecimal(((Number) a[9]).doubleValue()));
            act.setReviewCount((Integer) a[10]);
            act.setStatus("published");
            activityRepository.save(act);
        }
    }

    private void seedBlogs() {
        Object[][] data = {
            {"10 Best Places to Visit in Rajasthan", "10-best-places-rajasthan", "Destinations", "Priya Sharma", "Discover the majestic forts and palaces of Rajasthan.", "Rajasthan, the land of kings, is one of India's most enchanting destinations. From Jaipur's Amber Fort to Jaisalmer's golden sand dunes, every city has a story.", "https://images.unsplash.com/photo-1477587458883-47145ed94245?w=600", "8 min read"},
            {"Kerala Backwater Guide", "kerala-backwater-guide", "Travel Guide", "Rahul Mehta", "Complete guide to Kerala's magical backwaters.", "The backwaters of Kerala are a network of lakes, canals, and lagoons. A houseboat cruise is an unforgettable experience.", "https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?w=600", "6 min read"},
            {"Budget Travel: Thailand on 40K", "budget-thailand-40k", "Budget Tips", "Ananya Reddy", "How to explore Thailand without breaking the bank.", "Thailand offers incredible value. From Bangkok street food to Phuket beaches, here's how to do it on a budget.", "https://images.unsplash.com/photo-1528181304800-259b08848526?w=600", "5 min read"},
            {"Solo Travel Safety Tips for India", "solo-travel-safety-india", "Tips", "Vikram Singh", "Essential safety tips for solo travelers.", "India is a rewarding destination for solo travelers. Follow these practical tips for a safe and enriching trip.", "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?w=600", "7 min read"},
        };
        for (Object[] b : data) {
            Blog blog = new Blog();
            blog.setTitle((String) b[0]);
            blog.setSlug((String) b[1]);
            blog.setCategory((String) b[2]);
            blog.setAuthor((String) b[3]);
            blog.setExcerpt((String) b[4]);
            blog.setContent((String) b[5]);
            blog.setImage((String) b[6]);
            blog.setReadTime((String) b[7]);
            blog.setStatus("published");
            blogRepository.save(blog);
        }
    }

    private void seedMenus() {
        // Get all published destinations
        List<Destination> allDests = destinationRepository.findByStatus("published");

        // India menu
        List<Destination> indiaDests = allDests.stream()
            .filter(d -> "domestic".equals(d.getType())).collect(java.util.stream.Collectors.toList());
        Menu india = createMenu("India", "india", "Explore the incredible diversity of India", "icon-india", 1,
            "Discover India", "Explore India's majestic heritage, serene backwaters, pristine beaches, and snow-capped mountains.",
            "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?w=1200",
            "India Travel - Book Tour Packages, Destinations & Experiences | TravelVista",
            "Explore India travel packages — Rajasthan heritage tours, Kerala backwaters, Goa beaches, Ladakh adventures. Book with TravelVista.",
            "India travel, India tour packages, Rajasthan, Kerala, Goa, Ladakh, India holidays");
        india.setDestinations(indiaDests);
        menuRepository.save(india);

        // International menu
        List<Destination> intlDests = allDests.stream()
            .filter(d -> "international".equals(d.getType())).collect(java.util.stream.Collectors.toList());
        Menu intl = createMenu("International", "international", "Discover amazing destinations around the world", "icon-intl", 2,
            "International Travel", "Explore breathtaking international destinations — from Swiss Alps to Thai beaches.",
            "https://images.unsplash.com/photo-1530122037265-a5f1f91d3b99?w=1200",
            "International Travel Packages - Dubai, Thailand, Switzerland, Japan | TravelVista",
            "International travel packages — Dubai luxury tours, Thailand beaches, Switzerland Alps, Japan cultural odyssey. Book with TravelVista.",
            "international travel, Dubai packages, Thailand tour, Switzerland travel, Japan tour");
        intl.setDestinations(intlDests);
        menuRepository.save(intl);

        // Packages menu
        createMenu("Packages", "packages", "Browse all travel packages by category", "icon-packages", 3,
            "Travel Packages", "Find the perfect travel package for your next adventure, honeymoon, or family holiday.",
            "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=1200",
            "Travel Packages - Domestic & International Holiday Packages | TravelVista",
            "Browse travel packages — domestic India, international, family, honeymoon, luxury, adventure packages.",
            "travel packages, holiday packages, domestic packages, international packages, honeymoon packages");

        // Holiday menu
        createMenu("Holiday", "holiday", "Plan your perfect holiday getaway", "icon-holiday", 4,
            "Holidays", "From family getaways to romantic honeymoons, find the perfect holiday package.",
            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200",
            "Holiday Packages - Family, Honeymoon, Beach, Adventure Holidays | TravelVista",
            "Holiday packages — family holidays, honeymoon, beach holidays, adventure, luxury, weekend getaways.",
            "holiday packages, family holiday, honeymoon holiday, beach holiday, adventure holiday");

        // MICE menu
        createMenu("MICE", "mice", "Corporate meetings, incentives, conferences & exhibitions", "icon-mice", 5,
            "MICE Travel", "Professional corporate travel solutions for meetings, incentives, conferences, and exhibitions.",
            "https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=1200",
            "MICE Travel - Corporate Events, Conferences, Exhibitions | TravelVista",
            "MICE travel — corporate meetings, incentives, conferences, exhibitions, team building.",
            "MICE travel, corporate events, conference planning, exhibition travel, corporate meetings");

        // Destination Wedding menu
        createMenu("Destination Wedding", "destination-wedding", "Dream wedding destinations for your special day", "icon-wedding", 6,
            "Destination Weddings", "Make your dream wedding come true at breathtaking destinations across India and the world.",
            "https://images.unsplash.com/photo-1519741497674-611481863552?w=1200",
            "Destination Wedding Packages - Udaipur, Goa, Rajasthan, Bali | TravelVista",
            "Destination wedding packages — Udaipur palaces, Goa beaches, Rajasthan forts, Bali resorts.",
            "destination wedding, wedding packages, Udaipur wedding, Goa wedding, Rajasthan wedding");

        // Medical Tourism menu
        createMenu("Medical Tourism", "medical-tourism", "World-class medical treatment with travel assistance", "icon-medical", 7,
            "Medical Tourism", "Access world-class medical care with comprehensive travel support and accommodation.",
            "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=1200",
            "Medical Tourism - Treatment Packages, Hospital Assistance | TravelVista",
            "Medical tourism — cardiac care, orthopedic, cosmetic surgery, hospital assistance, visa support.",
            "medical tourism, medical travel, hospital packages, treatment abroad, patient travel");

        System.out.println("  ✅ 7 main menus seeded");
    }

    private Menu createMenu(String name, String slug, String desc, String icon, int order,
                            String pageTitle, String pageSubtitle, String heroImage,
                            String seoTitle, String seoDesc, String seoKeywords) {
        Menu m = new Menu();
        m.setName(name);
        m.setSlug(slug);
        m.setDescription(desc);
        m.setIcon(icon);
        m.setDisplayOrder(order);
        m.setStatus("published");
        m.setPageTitle(pageTitle);
        m.setPageSubtitle(pageSubtitle);
        m.setPageHeroImage(heroImage);
        m.setSeoTitle(seoTitle);
        m.setSeoDescription(seoDesc);
        m.setSeoKeywords(seoKeywords);
        return m;
    }

    private void seedSettings() {
        String[][] settings = {
            {"brand_name", "TravelVista"},
            {"tagline", "Explore the World with Confidence"},
            {"phone", "+91 98765 43210"},
            {"whatsapp", "+91 98765 43210"},
            {"email", "hello@travelvista.com"},
            {"address", "42, Marine Drive, Mumbai, Maharashtra 400001"},
            {"business_hours", "Mon-Sat: 9:00 AM - 8:00 PM IST"},
            {"facebook", "https://facebook.com/travelvista"},
            {"instagram", "https://instagram.com/travelvista"},
            {"youtube", "https://youtube.com/travelvista"},
            {"twitter", "https://twitter.com/travelvista"},
        };

        for (String[] s : settings) {
            siteSettingRepository.save(new SiteSetting(s[0], s[1]));
        }
    }
}
