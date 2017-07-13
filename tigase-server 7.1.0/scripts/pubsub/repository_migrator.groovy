#!/usr/bin/env groovy
/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

def tigase_config = "/home/smoku/workspace/tigase-server/etc/tigase-mysql.xml"
def component_name = "pubsub"


import tigase.db.*
import tigase.conf.*
import tigase.pubsub.NodeType
import tigase.pubsub.PubSubComponent
import tigase.pubsub.PubSubConfig
import tigase.pubsub.repository.PubSubDAO
import tigase.pubsub.repository.PubSubDAOJDBC
import tigase.pubsub.repository.RepositoryException


def conf_repo = new ConfigRepository(false, tigase_config)
def user_repo = RepositoryFactory.getUserRepository("util",
			conf_repo.get("basic-conf", null, Configurable.USER_REPO_CLASS_PROP_KEY, null),
			conf_repo.get("basic-conf", null, Configurable.USER_REPO_URL_PROP_KEY, null),
			null)

def cls_name = conf_repo.get(component_name, null, PubSubComponent.PUBSUB_REPO_CLASS_PROP_KEY, null)
def res_uri  = conf_repo.get(component_name, null, PubSubComponent.PUBSUB_REPO_URL_PROP_KEY, null)

if (cls_name != "tigase.pubsub.repository.PubSubDAOJDBC") {
	println "You need to use 'tigase.pubsub.repository.PubSubDAOJDBC' as PubSub data repository"

} else {
	def config = new PubSubConfig()
	config.setServiceName("tigase-pubsub")
	def sourceDAO = new PubSubDAO(user_repo, config)
	def destDAO = new PubSubDAOJDBC(user_repo, config, res_uri)
	
	println "... BEGIN ..."
	sourceDAO.getNodesList().each { nodeName ->
		println "Node: $nodeName"
		try {
			NodeType type = NodeType.valueOf(sourceDAO.readNodeConfigForm(nodeName).getAsString("pubsub#node_type"))
			destDAO.createNode(nodeName, "", sourceDAO.getNodeConfig(nodeName), type, null)
		} catch (RepositoryException e) {
			println e
		}
		
		sourceDAO.getItemsIds(nodeName).each { id ->
			println "  ItemID: $id"
			destDAO.writeItem(nodeName, sourceDAO.getItemCreationDate(nodeName, id).getTime(), id,
					sourceDAO.getItemPublisher(nodeName, id), sourceDAO.getItem(nodeName, id))
		}
	
		int index = 0;
		while (true) {
			final String key = "subscriptions" + (index == 0 ? "" : ("." + index));
			String cnfData = user_repo.getData(config.getServiceName(), PubSubDAO.NODES_KEY + nodeName, key);
			if (cnfData == null || cnfData.length() == 0)
				break;
			destDAO.updateSubscriptions(nodeName, index, cnfData)
			println "  Sub[$index]: $cnfData"
			++index;
		}
	}
	
	println "... DONE ..."
}