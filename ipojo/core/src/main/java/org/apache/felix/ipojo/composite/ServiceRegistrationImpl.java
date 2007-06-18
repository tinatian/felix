/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.ipojo.composite;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.InstanceManager;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Internal service registration implemenation. This class is used for in the
 * composition.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ServiceRegistrationImpl implements ServiceRegistration {

    /**
     * Service Registry.
     */
    private ServiceRegistry m_registry = null;

    /**
     * Interfaces associated with the service object.
     */
    private String[] m_classes = null;

    /**
     * Service Id associated with the service object.
     */
    private Long m_serviceId = null;

    /**
     * Service object.
     */
    private Object m_svcObj = null;

    /**
     * Service factory interface.
     */
    private ServiceFactory m_factory = null;

    /**
     * Associated property dictionary.
     */
    private Map m_propMap = null;

    /**
     * Re-usable service reference.
     */
    private ServiceReferenceImpl m_ref = null;

    /**
     * Constructor.
     * 
     * @param registry : the service registry
     * @param cm : component instance
     * @param classes : published interfaces array
     * @param serviceId : the unique service id
     * @param svcObj : the service object or the service factory object
     * @param dict : service properties
     */
    public ServiceRegistrationImpl(ServiceRegistry registry, ComponentInstance cm, String[] classes, Long serviceId, Object svcObj, Dictionary dict) {
        m_registry = registry;
        m_classes = classes;
        m_serviceId = serviceId;
        m_svcObj = svcObj;
        if (m_svcObj instanceof ServiceFactory) { m_factory = (ServiceFactory) m_svcObj; }
        initializeProperties(dict);

        // This reference is the "standard" reference for this service and will
        // always be returned by getReference().
        // Since all reference to this service are supposed to be equal, we use
        // the hashcode of this reference for
        // a references to this service in ServiceReference.
        m_ref = new ServiceReferenceImpl(cm, this);
    }

    /**
     * Check if the service registration still valid.
     * @return true if the service registration is valid.
     */
    protected boolean isValid() {
        return m_svcObj != null;
    }

    /**
     * Get the service reference attached with this service registration.
     * @return the service reference
     * @see org.osgi.framework.ServiceRegistration#getReference()
     */
    public ServiceReference getReference() {
        return m_ref;
    }

    /**
     * Add properties to a service registration.
     * @param dict : the properties to add
     * @see org.osgi.framework.ServiceRegistration#setProperties(java.util.Dictionary)
     */
    public void setProperties(Dictionary dict) {
        // Make sure registration is valid.
        if (!isValid()) {
            throw new IllegalStateException("The service registration is no longer valid.");
        }
        // Set the properties.
        initializeProperties(dict);
        // Tell registry about it.
        m_registry.servicePropertiesModified(this);
    }

    /**
     * Unregister the service.
     * @see org.osgi.framework.ServiceRegistration#unregister()
     */
    public void unregister() {
        if (m_svcObj != null) {
            m_registry.unregisterService(this);
            m_svcObj = null;
            m_factory = null;
        } else {
            throw new IllegalStateException("Service already unregistered.");
        }
    }

    /**
     * Look for a property in the service properties.
     * 
     * @param key : property key
     * @return the object associated with the key or null if the key is not
     * present.
     */
    protected Object getProperty(String key) {
        return m_propMap.get(key);
    }

    /**
     * Property Keys List.
     */
    private transient ArrayList m_list = new ArrayList();

    /**
     * Get the property keys.
     * @return the property keys list.
     */
    protected String[] getPropertyKeys() {
        synchronized (m_propMap) {
            m_list.clear();
            Iterator i = m_propMap.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry) i.next();
                m_list.add(entry.getKey());
            }
            return (String[]) m_list.toArray(new String[m_list.size()]);
        }
    }

    /**
     * Get the service object.
     * @return the service object. Call the service factory if needed.
     */
    protected Object getService() {
        // If the service object is a service factory, then
        // let it create the service object.
        if (m_factory != null) {
            return getFactoryUnchecked();
        } else {
            return m_svcObj;
        }
    }

    /**
     * Initialize properties.
     * 
     * @param dict : serivce properties to publish.
     */
    private void initializeProperties(Dictionary dict) {
        // Create a case insensitive map.
        if (m_propMap == null) {
            m_propMap = new StringMap(false);
        } else {
            m_propMap.clear();
        }

        if (dict != null) {
            Enumeration keys = dict.keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                m_propMap.put(key, dict.get(key));
            }
        }
        // Add the framework assigned properties.
        m_propMap.put(Constants.OBJECTCLASS, m_classes);
        m_propMap.put(Constants.SERVICE_ID, m_serviceId);
    }

    /**
     * Get a service object via a service factory.
     * @return the service object via the service factory invocation.
     */
    private Object getFactoryUnchecked() {
        return m_factory.getService(null, this);
    }

    /**
     * Unget a service. (Internal Method)
     * 
     * @param cm : component instance using the service.
     * @param svcObj : the unget service object.
     */
    private void ungetFactoryUnchecked(ComponentInstance cm, Object svcObj) {
        if (cm instanceof InstanceManager) {
            m_factory.ungetService(((InstanceManager) cm).getContext().getBundle(), this, svcObj);
        }

    }

    /**
     * Unget a service.
     * 
     * @param cm : component instance using the service.
     * @param srvObj : the unget service object.
     */
    public void ungetService(ComponentInstance cm, Object srvObj) {
        // If the service object is a service factory, then let is release the
        // service object.
        if (m_factory != null) {
            ungetFactoryUnchecked(cm, srvObj);
        }
    }

}
