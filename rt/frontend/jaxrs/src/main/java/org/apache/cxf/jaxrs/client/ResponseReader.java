/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.jaxrs.client;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Providers;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

public class ResponseReader implements MessageBodyReader<Response> {

    @Context
    private MessageContext context;
 
    private Class<?> entityCls;
    private Type entityGenericType;
    
    public ResponseReader() {
        
    }
    
    public ResponseReader(Class<?> entityCls) {
        this.entityCls = entityCls;
    }
    
    public boolean isReadable(Class<?> cls, Type genericType, Annotation[] anns, MediaType mt) {
        return cls.isAssignableFrom(Response.class);
    }

    @SuppressWarnings("unchecked")
    public Response readFrom(Class<Response> cls, Type genericType, Annotation[] anns, MediaType mt,
        MultivaluedMap<String, String> headers, InputStream is) 
        throws IOException, WebApplicationException {
        
        int status = Integer.valueOf(getContext().get(Message.RESPONSE_CODE).toString());
        
        ResponseBuilder rb = Response.status(status);
        
        for (String header : headers.keySet()) {
            List<String> values = headers.get(header);
            for (String value : values) {
                rb.header(header, value);
            }
        }
        
        if (entityCls != null) {
            Providers providers = getContext().getProviders();
            MessageBodyReader<?> reader = 
                providers.getMessageBodyReader(entityCls, getEntityGenericType(), anns, mt);
            if (reader == null) {
                throw new ClientWebApplicationException("No reader for Response entity "
                                                        + entityCls.getName());
            }
            
            Object entity = reader.readFrom((Class)entityCls, getEntityGenericType(), 
                                            anns, mt, headers, is);
            rb.entity(entity);
        }
        
        
        return rb.build();
    }

    public void setEntityClass(Class<?> cls) {
        entityCls = cls;
    }
    
    private Type getEntityGenericType() {
        return entityGenericType == null ? entityCls : entityGenericType; 
    }
    
    protected MessageContext getContext() {
        return context;
    }
    
    /**
     * 
     * @param client the client
     * @param response {@link Response} object with the response input stream
     * @param cls the entity class
     * @return the typed entity
     */
    @SuppressWarnings("unchecked")
    public <T> T readEntity(Client client, Response response, Class<T> cls) {
        Class<?> oldClass = entityCls;
        setEntityClass(cls);
        try {
            MultivaluedMap headers = response.getMetadata();
            Object contentType = headers.getFirst("Content-Type");
            InputStream inputStream = (InputStream)response.getEntity();
            if (contentType == null || inputStream == null) {
                return null;
            }
            Annotation[] annotations = new Annotation[]{};
            MediaType mt = MediaType.valueOf(contentType.toString());
            
            Endpoint ep = WebClient.getConfig(client).getConduitSelector().getEndpoint();
            Exchange exchange = new ExchangeImpl();
            Message inMessage = new MessageImpl();
            inMessage.setExchange(exchange);
            exchange.put(Endpoint.class, ep);
            exchange.setOutMessage(new MessageImpl());
            exchange.setInMessage(inMessage);
            inMessage.put(Message.REQUESTOR_ROLE, Boolean.TRUE);
            inMessage.put(Message.PROTOCOL_HEADERS, headers);
            
            ProviderFactory pf = (ProviderFactory)ep.get(ProviderFactory.class.getName());
            
            MessageBodyReader reader = pf.createMessageBodyReader(entityCls, 
                                                             entityCls, 
                                                             annotations, 
                                                             mt, 
                                                             inMessage);
            
            
            
            if (reader == null) {
                return null;
            }
            
            return (T)reader.readFrom(entityCls, entityCls, annotations, mt, 
                                      (MultivaluedMap<String, String>)headers, 
                                      inputStream);
        } catch (Exception ex) {
            throw new ClientWebApplicationException(ex);
        } finally {
            entityCls = oldClass;
        }
    }
}
