/*
 * Copyright 2008-2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.server.http.gui;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import voldemort.server.VoldemortServer;
import voldemort.server.http.VoldemortServletContextListener;
import voldemort.server.storage.StorageService;
import voldemort.store.readonly.RandomAccessFileStore;
import voldemort.utils.Utils;

import com.google.common.collect.Maps;

public class ReadOnlyStoreManagementServlet extends HttpServlet {

    private static final long serialVersionUID = 1;

    private Map<String, RandomAccessFileStore> stores;
    private VelocityEngine velocityEngine;

    public ReadOnlyStoreManagementServlet(VoldemortServer server, VelocityEngine engine) {
        this.stores = getReadOnlyStores(server);
        this.velocityEngine = Utils.notNull(engine);
    }

    @Override
    public void init() throws ServletException {
        super.init();
        this.stores = getReadOnlyStores((VoldemortServer) getServletContext().getAttribute(VoldemortServletContextListener.SERVER_CONFIG_KEY));
        this.velocityEngine = (VelocityEngine) Utils.notNull(getServletContext().getAttribute(VoldemortServletContextListener.VELOCITY_ENGINE_KEY));
    }

    private Map<String, RandomAccessFileStore> getReadOnlyStores(VoldemortServer server) {
        StorageService storage = (StorageService) Utils.notNull(server)
                                                       .getService("storage-service");
        return storage.getReadOnlyStores();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        Map<String, Object> params = Maps.newHashMap();
        params.put("stores", stores);
        velocityEngine.render("read-only-mgmt.vm", params, resp.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if("swap".equals(getRequired(req, "operation"))) {
            String indexFile = getRequired(req, "index");
            String dataFile = getRequired(req, "data");
            String storeName = getRequired(req, "store");
            if(!stores.containsKey(storeName))
                throw new ServletException("'" + storeName
                                           + "' is not a registered read-only store.");
            if(!Utils.isReadableFile(indexFile))
                throw new ServletException("Index file '" + indexFile + "' is not a readable file.");
            if(!Utils.isReadableFile(dataFile))
                throw new ServletException("Data file '" + dataFile + "' is not a readable file.");

            RandomAccessFileStore store = stores.get(storeName);
            store.swapFiles(indexFile, dataFile);
            resp.getWriter().write("Swap completed.");
        } else {
            throw new IllegalArgumentException("Unknown operation parameter: "
                                               + req.getParameter("operation"));
        }
    }

    private String getRequired(HttpServletRequest req, String name) throws ServletException {
        String val = req.getParameter(name);
        if(name == null)
            throw new ServletException("Missing required paramter '" + name + "'.");
        return val;
    }
}
