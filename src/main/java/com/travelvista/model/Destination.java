package com.travelvista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(unique = true, nullable = false, length = 220)
    private String slug;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String type = "domestic";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(length = 500)
    private String image;

    @Column(length = 300)
    private String tagline;

    @Column(length = 100)
    private String bestTime;

    @Column(length = 100)
    private String avgTemp;

    @Column(length = 200)
    private String languages;

    @Column(columnDefinition = "TEXT")
    private String highlights;

    @Column(name = "package_count")
    private Integer packageCount = 0;

    @Column(length = 20)
    private String status = "draft";

    private Boolean featured = false;

    @Column(name = "is_famous")
    private Boolean isFamous = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // ═══ CMS FIELDS ═══

    // Hero section - up to 3 images (comma-separated URLs), hero title, subtitle, CTA
    @Column(name = "hero_images", columnDefinition = "TEXT")
    private String heroImages; // JSON array of image URLs

    @Column(name = "hero_title", length = 200)
    private String heroTitle;

    @Column(name = "hero_subtitle", length = 500)
    private String heroSubtitle;

    @Column(name = "hero_cta_text", length = 100)
    private String heroCtaText;

    @Column(name = "hero_cta_url", length = 500)
    private String heroCtaUrl;

    // About section
    @Column(name = "about_title", length = 200)
    private String aboutTitle;

    @Column(name = "about_content", columnDefinition = "TEXT")
    private String aboutContent;

    @Column(name = "about_image", length = 500)
    private String aboutImage;

    @Column(name = "about_image_position", length = 20)
    private String aboutImagePosition = "right"; // left or right

    // Attractions - JSON array
    @Column(name = "attractions", columnDefinition = "TEXT")
    private String attractions; // JSON: [{title, image, description, location, displayOrder, isActive}]

    // Food & Local Experiences - JSON array
    @Column(name = "experiences", columnDefinition = "TEXT")
    private String experiences; // JSON: [{title, image, description, category, price, duration, location, displayOrder, isActive}]

    // Highlights - JSON array
    @Column(name = "destination_highlights", columnDefinition = "TEXT")
    private String destinationHighlights; // JSON: [{title, description, icon, displayOrder, isActive}]

    // Quick Info - JSON array
    @Column(name = "quick_info", columnDefinition = "TEXT")
    private String quickInfo; // JSON: [{label, value, icon, displayOrder}]

    // ═══ SEO FIELDS ═══
    @Column(name = "seo_title", length = 200)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keywords", length = 500)
    private String seoKeywords;

    @Column(name = "og_title", length = 200)
    private String ogTitle;

    @Column(name = "og_description", length = 500)
    private String ogDescription;

    @Column(name = "og_image", length = 500)
    private String ogImage;

    @Column(name = "twitter_title", length = 200)
    private String twitterTitle;

    @Column(name = "twitter_description", length = 500)
    private String twitterDescription;

    @Column(name = "twitter_image", length = 500)
    private String twitterImage;

    @Column(name = "canonical_url", length = 500)
    private String canonicalUrl;

    @Column(name = "no_index")
    private Boolean noIndex = false;

    @Column(name = "no_follow")
    private Boolean noFollow = false;

    @Column(name = "schema_type", length = 50)
    private String schemaType = "TouristDestination";

    @Column(name = "breadcrumb_text", length = 200)
    private String breadcrumbText;

    @Column(name = "h1_text", length = 200)
    private String h1Text;

    @Column(name = "internal_links", columnDefinition = "TEXT")
    private String internalLinks; // JSON: [{slug, title, type}]

    @Column(name = "related_destinations", columnDefinition = "TEXT")
    private String relatedDestinations; // JSON: [{slug, name, image}]

    // Section visibility toggles
    @Column(name = "show_attractions")
    private Boolean showAttractions = true;

    @Column(name = "show_experiences")
    private Boolean showExperiences = true;

    @Column(name = "show_highlights")
    private Boolean showHighlights = true;

    @Column(name = "show_packages")
    private Boolean showPackages = true;

    @Column(name = "show_quick_info")
    private Boolean showQuickInfo = true;

    // Coming soon message for packages
    @Column(name = "packages_coming_soon")
    private Boolean packagesComingSoon = false;

    @Column(name = "packages_coming_soon_text", length = 500)
    private String packagesComingSoonText;

    // ═══ RELATIONSHIPS ═══
    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<HeroSlide> heroSlides = new ArrayList<>();

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<PageSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<DestinationFaq> faqs = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Destination() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getBestTime() { return bestTime; }
    public void setBestTime(String bestTime) { this.bestTime = bestTime; }
    public String getAvgTemp() { return avgTemp; }
    public void setAvgTemp(String avgTemp) { this.avgTemp = avgTemp; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public String getHighlights() { return highlights; }
    public void setHighlights(String highlights) { this.highlights = highlights; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getFeatured() { return featured; }
    public void setFeatured(Boolean featured) { this.featured = featured; }
    public Boolean getIsFamous() { return isFamous; }
    public void setIsFamous(Boolean isFamous) { this.isFamous = isFamous; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // CMS getters and setters
    public String getHeroImages() { return heroImages; }
    public void setHeroImages(String heroImages) { this.heroImages = heroImages; }
    public String getHeroTitle() { return heroTitle; }
    public void setHeroTitle(String heroTitle) { this.heroTitle = heroTitle; }
    public String getHeroSubtitle() { return heroSubtitle; }
    public void setHeroSubtitle(String heroSubtitle) { this.heroSubtitle = heroSubtitle; }
    public String getHeroCtaText() { return heroCtaText; }
    public void setHeroCtaText(String heroCtaText) { this.heroCtaText = heroCtaText; }
    public String getHeroCtaUrl() { return heroCtaUrl; }
    public void setHeroCtaUrl(String heroCtaUrl) { this.heroCtaUrl = heroCtaUrl; }
    public String getAboutTitle() { return aboutTitle; }
    public void setAboutTitle(String aboutTitle) { this.aboutTitle = aboutTitle; }
    public String getAboutContent() { return aboutContent; }
    public void setAboutContent(String aboutContent) { this.aboutContent = aboutContent; }
    public String getAboutImage() { return aboutImage; }
    public void setAboutImage(String aboutImage) { this.aboutImage = aboutImage; }
    public String getAboutImagePosition() { return aboutImagePosition; }
    public void setAboutImagePosition(String aboutImagePosition) { this.aboutImagePosition = aboutImagePosition; }
    public String getAttractions() { return attractions; }
    public void setAttractions(String attractions) { this.attractions = attractions; }
    public String getExperiences() { return experiences; }
    public void setExperiences(String experiences) { this.experiences = experiences; }
    public String getDestinationHighlights() { return destinationHighlights; }
    public void setDestinationHighlights(String destinationHighlights) { this.destinationHighlights = destinationHighlights; }
    public String getQuickInfo() { return quickInfo; }
    public void setQuickInfo(String quickInfo) { this.quickInfo = quickInfo; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
    public String getOgTitle() { return ogTitle; }
    public void setOgTitle(String ogTitle) { this.ogTitle = ogTitle; }
    public String getOgDescription() { return ogDescription; }
    public void setOgDescription(String ogDescription) { this.ogDescription = ogDescription; }
    public String getOgImage() { return ogImage; }
    public void setOgImage(String ogImage) { this.ogImage = ogImage; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public Boolean getNoIndex() { return noIndex; }
    public void setNoIndex(Boolean noIndex) { this.noIndex = noIndex; }
    public Boolean getShowAttractions() { return showAttractions; }
    public void setShowAttractions(Boolean showAttractions) { this.showAttractions = showAttractions; }
    public Boolean getShowExperiences() { return showExperiences; }
    public void setShowExperiences(Boolean showExperiences) { this.showExperiences = showExperiences; }
    public Boolean getShowHighlights() { return showHighlights; }
    public void setShowHighlights(Boolean showHighlights) { this.showHighlights = showHighlights; }
    public Boolean getShowPackages() { return showPackages; }
    public void setShowPackages(Boolean showPackages) { this.showPackages = showPackages; }
    public Boolean getShowQuickInfo() { return showQuickInfo; }
    public void setShowQuickInfo(Boolean showQuickInfo) { this.showQuickInfo = showQuickInfo; }
    public Boolean getPackagesComingSoon() { return packagesComingSoon; }
    public void setPackagesComingSoon(Boolean packagesComingSoon) { this.packagesComingSoon = packagesComingSoon; }
    public String getPackagesComingSoonText() { return packagesComingSoonText; }
    public void setPackagesComingSoonText(String packagesComingSoonText) { this.packagesComingSoonText = packagesComingSoonText; }

    // SEO getters and setters
    public String getSeoKeywords() { return seoKeywords; }
    public void setSeoKeywords(String seoKeywords) { this.seoKeywords = seoKeywords; }
    public String getTwitterTitle() { return twitterTitle; }
    public void setTwitterTitle(String twitterTitle) { this.twitterTitle = twitterTitle; }
    public String getTwitterDescription() { return twitterDescription; }
    public void setTwitterDescription(String twitterDescription) { this.twitterDescription = twitterDescription; }
    public String getTwitterImage() { return twitterImage; }
    public void setTwitterImage(String twitterImage) { this.twitterImage = twitterImage; }
    public Boolean getNoFollow() { return noFollow; }
    public void setNoFollow(Boolean noFollow) { this.noFollow = noFollow; }
    public String getSchemaType() { return schemaType; }
    public void setSchemaType(String schemaType) { this.schemaType = schemaType; }
    public String getBreadcrumbText() { return breadcrumbText; }
    public void setBreadcrumbText(String breadcrumbText) { this.breadcrumbText = breadcrumbText; }
    public String getH1Text() { return h1Text; }
    public void setH1Text(String h1Text) { this.h1Text = h1Text; }
    public String getInternalLinks() { return internalLinks; }
    public void setInternalLinks(String internalLinks) { this.internalLinks = internalLinks; }
    public String getRelatedDestinations() { return relatedDestinations; }
    public void setRelatedDestinations(String relatedDestinations) { this.relatedDestinations = relatedDestinations; }

    // Relationship getters
    public List<HeroSlide> getHeroSlides() { return heroSlides; }
    public void setHeroSlides(List<HeroSlide> heroSlides) { this.heroSlides = heroSlides; }
    public List<PageSection> getSections() { return sections; }
    public void setSections(List<PageSection> sections) { this.sections = sections; }
    public List<DestinationFaq> getFaqs() { return faqs; }
    public void setFaqs(List<DestinationFaq> faqs) { this.faqs = faqs; }
}
