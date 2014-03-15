
/*
 *  Copyright 2012 Hinttech B.V.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.hinttech.hippo.hst.util;

import java.util.List;

import javax.jcr.RepositoryException;

import org.hippoecm.hst.component.support.bean.BaseHstComponent;
import org.hippoecm.hst.container.RequestContextProvider;
import org.hippoecm.hst.content.beans.ObjectBeanManagerException;
import org.hippoecm.hst.content.beans.manager.ObjectConverter;
import org.hippoecm.hst.content.beans.standard.HippoBean;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.sitemapitemhandler.AbstractHstSiteMapHandler;
import org.hippoecm.hst.util.ObjectConverterUtils;
import org.hippoecm.hst.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hippoecm.hst.jaxrs.util.AnnotatedContentBeanClassesScanner.scanAnnotatedContentBeanClasses;

/**
 */
public abstract class ContentBeanAwareHstSiteMapItemHandler extends AbstractHstSiteMapHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentBeanAwareHstSiteMapItemHandler.class);
    private ObjectConverter objectConverter;
    private List<Class<? extends HippoBean>> annotatedClasses;

    /**
     * Resolves the content bean for the currently ResolvedSiteMapItem. This grabs the current HstRequestContext
     * from the ThreadLocal variable. A HstRequestContext must be available in this Thread for this method to work.
     * This should always be the case in the case of a HstSiteMapItemHandler.
     * @return the content bean for the resolved site map item of the current request context 
     */
    protected HippoBean getContentBeanForResolvedSiteMapItem() {
        HstRequestContext requestContext = RequestContextProvider.get();
        ObjectConverter converter = getObjectConverter(requestContext);

        String base = PathUtils.normalizePath(requestContext.getResolvedMount().getMount().getContentPath());
        String relPath = PathUtils.normalizePath(requestContext.getResolvedSiteMapItem().getRelativeContentPath());
        if (relPath == null) {
            return null;
        }
        try {
            if ("".equals(relPath)) {
                return (HippoBean) converter.getObject(requestContext.getSession(), "/" + base);
            } else {
                return (HippoBean) converter.getObject(requestContext.getSession(), "/" + base + "/" + relPath);
            }
        } catch (ObjectBeanManagerException e) {
            LOGGER.error("ObjectBeanManagerException. Return null : {}", e);
        } catch (RepositoryException e) {
            LOGGER.error("Could not get bean for path " + relPath, e);
        }
        return null;

    }

    private ObjectConverter getObjectConverter(HstRequestContext requestContext) {
        if (objectConverter == null) {
            objectConverter = ObjectConverterUtils.createObjectConverter(getAnnotatedClasses(requestContext));
        }
        return objectConverter;
    }

    private List<Class<? extends HippoBean>> getAnnotatedClasses(HstRequestContext requestContext) {
        if (annotatedClasses == null) {
            String beansAnnotatedClassesConfParameter =
                    requestContext.getServletContext().getInitParameter(
                            BaseHstComponent.BEANS_ANNOTATED_CLASSES_CONF_PARAM
                    );

            annotatedClasses = scanAnnotatedContentBeanClasses(
                    requestContext,
                    beansAnnotatedClassesConfParameter);
        }
        return annotatedClasses;
    }
}

