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

package voldemort.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import voldemort.cluster.Node;

/**
 * A routing strategy which just routes each request to all the nodes given
 * 
 * @author jay
 * 
 */
public class RouteToAllStrategy implements RoutingStrategy {

    private Collection<Node> nodes;

    public RouteToAllStrategy(Collection<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Node> routeRequest(byte[] key) {
        return new ArrayList<Node>(nodes);
    }

    public Set<Node> getNodes() {
        return new HashSet<Node>(nodes);
    }

}
