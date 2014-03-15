---
layout: post
title:  "Reduce the Complexity of Your Templates Using an HstSiteMapItemHandler"
date:   2012-04-22 12:00:31
categories: hippo cms web
redirect_from: "/2012/04/changing-your-rendering-based-on.html"
summary: "Often the content in the document that's being rendered on the page helps decide what information ends up on the user's screen. We normally have two ways to handle this: in the java component (HstComponent) or in the render template (JSP / Freemarker). Now this works OK when the structure of the page doesn't change, because it's mostly a matter of changing what is put in the attributes of the request (Java) or writing a &lt;c:if&gt; in your JSP / Freemarker."
---

Often the content in the document that's being rendered on the page helps decide what information ends up on the user's screen. We normally have two ways to handle this: in the java component (HstComponent) or in the render template (JSP / Freemarker). Now this works OK when the structure of the page doesn't change, because it's mostly a matter of changing what is put in the attributes of the request (Java) or writing a &lt;c:if&gt; in your JSP / Freemarker.
At my current project we had a different requirement: there are about 400 different subjects and those are either "current" or "standard" and based on this, those subjects should have a completely different rendering, to a point where components are in completely different places or simply don't even get rendered. Another requirement is that both current and standard subjects should still exist within the same url space (subjects/*) and their url shouldn't change when their "type" changes from standard to current or back.

### A possible solution - add everything to one configuration
A possible solution would have been to create one subject-page-definition that merges the structure of both the standard and the current configurations. Then in the JSP we could use if-statements to render certain components in certain parts of the page based on the "type" attribute in the Subject bean. This solution would work, but it means all kinds on unnecessary code being executed and it really clutters your templates, making them less maintainable.

### Introducing the HstSiteMapItemHandler
The HstSiteMapItemHandler is part of the Hippo HST Api. It allows you to change the ResolvedSiteMapItem that is passed to the HST to handle the request. Handlers can be configured per site configuration and are added to sitemapitems by configuration.
Below is the HstSiteMapItemHandler interface:

{% highlight java %}
public interface HstSiteMapItemHandler {
    void init(ServletContext servletContext, SiteMapItemHandlerConfiguration handlerConfig) throws HstSiteMapItemHandlerException;
    ResolvedSiteMapItem process(ResolvedSiteMapItem resolvedSiteMapItem, HttpServletRequest request, HttpServletResponse response) throws HstSiteMapItemHandlerException;
    SiteMapItemHandlerConfiguration getSiteMapItemHandlerConfiguration();
    ServletContext getServletContext();
    void destroy() throws HstSiteMapItemHandlerException;
}
{% endhighlight %}

The most important method is process(): it receives a ResolvedSiteMapItem, HttpServletRequest and HttpServletResponse and it returns a ResolvedSiteMapItem. The getServletContext() and getSiteMapItemHandlerConfiguration() methods are mostly there for utility, returning those things that got passed to the handler in the init() method. The destroy() method allows you to clean up stuff (like system resources / sessions). Hippo supplies an AbstractHstSiteMapHandler as well, which does the boilerplate stuff like init() and the getters for you. This means you can suffice with implementing the process() method most of the time, which is what I did. To get the HstSiteMapItemHandler working, we need to do two things:

1. Let the HST know that the handler exists and give it an ID
2. Add the handler's ID to one or more sitemap items so that it gets called when one of those items is requested.

### Configuring the HstSiteMapItemHandler
As you can see, configuring a HstSiteMapHandler is fairly trivial. The node needs to be under the hst:sitemapitemhandlers node under a hst:configuration. The only property under the node that is mandatory is the __hst:sitemapitemhandlerclassname__ property. This specifies the java class of your implementation. All the other properties under the node are stored in the SiteMapItemHandlerConfiguration that gets passed to your SiteMapItemHandler in the init() method. For this case, I created two properties "standard" and "current" with their values pointing to two different page configurations. My SiteMapItemHandler will then make sure that Subjects of type "current" get the one page configuration and Subjects of type "standard" get the other.

### Adding the SiteMapItemHandler to Our SiteMap items
You can activate a HstSiteMapItemHandler for a certain sitemap item (url-mapping) by adding the property __hst:sitemapitemhandlerids__ to your sitemap item. This is a multi value String property where every value is an ID of a SiteMapItemHandler. In our case, the handler is called __subject-page-configuration-switcher__. This way, when someone goes to /onderwerpen/something/something, the HST will first call the process() method of the specified HstSiteMapHandler and then use the returned ResolvedSiteMapItem to render the response.

### Decorating the ResolvedSiteMapItem
Our SiteMapItemHandler's process() method needs to return a ResolvedSiteMapItem that is then passed on to more handlers or the HST's request processing. One of the methods on the ResolvedSiteMapItem interface is getHstComponentConfiguration():

{% highlight java %}
/**
 * @return the root <code>HstComponentConfiguration</code> that is configured
 */
 HstComponentConfiguration getHstComponentConfiguration();
{% endhighlight %}

This allows you to specify a different HstComponentConfiguration than the one that was specified on the SiteMapItem which was the source of this ResolvedSiteMapItem. What I did was create a Decorator for the ResolvedSiteMapItem interface that allows you to override the HstComponentConfiguration that is returned and delegates the rest to the original ResolvedSiteMapItem. The snippet below shows a part of the implementation, the rest of the methods in the interface are simply delegated analogous to how getResolvedMount() is implemented.

{% highlight java %}
public class HstResolvedSiteMapItemDecorator implements ResolvedSiteMapItem {
    private final ResolvedSiteMapItem delegate;
    private final HstComponentConfiguration hstComponentConfiguration;
    private final String relativeContentPath;
    public HstResolvedSiteMapItemDecorator(final ResolvedSiteMapItem delegate, final HstComponentConfiguration componentConfiguration) {
        this(delegate, componentConfiguration, delegate.getRelativeContentPath());
    }
    public HstResolvedSiteMapItemDecorator(final ResolvedSiteMapItem delegate, final HstComponentConfiguration componentConfiguration, final String relativeContentPath) {
        this.delegate = delegate;
        this.hstComponentConfiguration = componentConfiguration;
        this.relativeContentPath = relativeContentPath;
    }
    @Override
    public HstComponentConfiguration getHstComponentConfiguration() {
        return hstComponentConfiguration;
    }
}
{% endhighlight %}

### Getting the ContentBean
The process() method gets passed three things: a ResolvedSiteMapItem, a ServletRequest and a ServletResponse. There are some utility methods that I got from Ard Schrijvers that should Soon(TM) be somewhere in the HST Utils Api that allow you to resolve the content bean for the current request. I had to add these to my custom SiteMapItemHandler for now. I ended up putting these utility methods in their own abstract class and extended that class for my new SiteMapItemHandler. All my class has to do is call the getContentBeanForResolvedSiteMapItem() method that returns a HippoBean to get the content bean for the current request. You can download the abstract class here: [ContentBeanAwareHstSiteMapItemHandler.java][contentbeanhandlersrc]

[contentbeanhandlersrc]: /attachments/ContentBeanAwareHstSiteMapItemHandler.java

### Putting It All Together
Now that everything is in place, we can implement the process() method. My implementation does the following:

1. Get the content bean for the current request;
2. If the content bean is a SubjectBean, then proceed, else return the ResolvedSiteMapItem that got passed to the process() method and log an error;
3. Based on the type of the Subject (String), get the property from the HandlerConfiguration with the same name as the type of the Subject;
4. If the property does not exist in the Handler configuration, return the ResolvedSiteMapItem that got passed to the process() method and log an error, else proceed;
5. Obtain the HstComponentConfiguration for the component id that was retrieved from the HandlerConfiguration, in this example either hst:pages/current or hst:pages/standard;
6. Return a new, decorated ResolvedSiteMapItem that returns the obtained HstComponentConfiguration.

Below is the source of the SiteMapItemHandler:

{% highlight java %}
public class SubjectHstSiteMapHandler extends ContentBeanAwareHstSiteMapItemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectHstSiteMapHandler.class);
    @Override
    public ResolvedSiteMapItem process(ResolvedSiteMapItem resolvedSiteMapItem, HttpServletRequest request, HttpServletResponse response) throws HstSiteMapItemHandlerException {
        HippoBean contentBean = getContentBeanForResolvedSiteMapItem(resolvedSiteMapItem);
        if (contentBean == null) {
            LOGGER.info("No content bean found, reverting to initial site map item.");
            return resolvedSiteMapItem;
        }
        boolean contentBeanIsSubject = contentBean instanceof SubjectBean;
        if (!contentBeanIsSubject) {
            LOGGER.error("Content bean is not a SubjectBean, so this handler cannot do anything, reverting to " +
                    "initial ResolvedSiteMapItem");
            return resolvedSiteMapItem;
        }
        SubjectBean subject = (SubjectBean) contentBean;
        String componentConfigurationId = deriveComponentConfigurationIdForSubject(resolvedSiteMapItem, subject);
        if (componentConfigurationId == null) {
            LOGGER.error("Subject of type {} cannot be resolved to a component configuration id, reverting to " +
                    "initial ResolvedSiteMapItem");
            return resolvedSiteMapItem;
        }
        HstComponentConfiguration componentConfiguration =
                ResolvedSiteMapItemUtils.obtainComponentConfiguration(resolvedSiteMapItem, componentConfigurationId);
        return new HstResolvedSiteMapItemDecorator(
                resolvedSiteMapItem,
                componentConfiguration
        );
    }
    private String deriveComponentConfigurationIdForSubject(final ResolvedSiteMapItem resolvedSiteMapItem,
                                                            final SubjectBean subject) {
        String subjectType = subject.getType();
        return getSiteMapItemHandlerConfiguration().getProperty(subjectType, resolvedSiteMapItem, String.class);
    }
}
{% endhighlight %}

### Conclusion 
HstSiteMapItemHandlers can be very powerful and can reduce the complexity of your page-configurations and render templates. They are especially useful when you want to have structurally different page renderings for url mappings that cannot consist of more than one sitemap item. The use case from my current project is a good example of this.
