/**
 * Linking parent records to child records.
 * 
 * Copyright (C) AK Bibliothek Wien 2015, Michael Birkner
 * 
 * This file is part of AkImporter.
 * 
 * AkImporter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AkImporter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AkImporter.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author   Michael Birkner <michael.birkner@akwien.at>
 * @license  http://www.gnu.org/licenses/gpl-3.0.html
 * @link     http://wien.arbeiterkammer.at/service/bibliothek/
 */
package main.java.betullam.akimporter.solrmab.relations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import main.java.betullam.akimporter.main.AkImporterHelper;

public class ParentToChilds {

	// General variables
	private HttpSolrServer solrServer;
	private Collection<SolrInputDocument> docsForAtomicUpdates = new ArrayList<SolrInputDocument>();
	private RelationHelper relationHelper;
	private int NO_OF_ROWS = 500;
	private int counter = 0;
	private long noOfDocs = 0;
	private boolean print = false;

	/**
	 * Constructor for indexing infos from parent records to child records
	 * 
	 * @param solrServer	Solr server we want to index to
	 * @param timeStamp		Timestamp of moment the import process started
	 * @param print			True if status messages should be printed to console
	 */
	public ParentToChilds(HttpSolrServer solrServer, String timeStamp, boolean print) {
		this.solrServer = solrServer;
		this.print = print;
		this.relationHelper = new RelationHelper(solrServer, null, timeStamp);
	}


	/**
	 * This actually adds the information of the parent records to its child records.
	 */
	public void addParentsToChilds() {

		SolrDocumentList queryResults = relationHelper.getCurrentlyIndexedChildRecords(true, null);

		// Show how many documents were found
		noOfDocs = queryResults.getNumFound();

		// If there are some records, go on. If not, do nothing.
		if (queryResults != null && noOfDocs > 0) {

			// Clear query results. We don't need them anymore.
			queryResults.clear();
			queryResults = null;

			// Calculate the number of solr result pages we need to iterate over
			long wholePages = (noOfDocs/NO_OF_ROWS);
			long fractionPages = (noOfDocs%NO_OF_ROWS);

			// Variable for lastDocId
			String lastDocId = null;

			for (long l = 0; l < wholePages; l++) {
				boolean isFirstPage = (l == 0) ? true : false;
				// Get the ID of the last document in the current page so that we can build a new filter query to iterate over the next page:
				lastDocId = linkParentsToChilds(isFirstPage, lastDocId);

				// Add documents to Solr
				relationHelper.indexDocuments(docsForAtomicUpdates, solrServer);

				// Set Collection<SolrInputDocument> to null and then to a fresh Collection
				docsForAtomicUpdates.clear();
				docsForAtomicUpdates = null;
				docsForAtomicUpdates = new ArrayList<SolrInputDocument>();
			}

			// Add documents on the last page:
			if (fractionPages != 0) {
				// If there is no whole page but only a fraction page, the fraction page is the first page, because it's the only one
				boolean isFirstPage = (wholePages <= 0) ? true : false;
				linkParentsToChilds(isFirstPage, lastDocId);

				// Add documents to Solr
				relationHelper.indexDocuments(docsForAtomicUpdates, solrServer);

				// Set Collection<SolrInputDocument> to null and then to a fresh Collection
				docsForAtomicUpdates.clear();
				docsForAtomicUpdates = null;
				docsForAtomicUpdates = new ArrayList<SolrInputDocument>();
			}

			// Commit the changes
			try {
				this.solrServer.commit();
			} catch (SolrServerException e) {
				System.err.println("Error while relating parents to childs");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("Error while relating parents to childs");
				e.printStackTrace();
			} finally {
				docsForAtomicUpdates.clear();
				docsForAtomicUpdates = null;
				queryResults = null;
			}
		}

	}


	/**
	 * Setting the documents for atomic Solr updates and returning the ID of the last processed Solr document
	 * 
	 * @param isFirstPage	True if first page of Solr results
	 * @param lastDocId		Doc Id of the previous last processed Solr document
	 * @return				Doc Id of the new last processed Solr document
	 */
	public String linkParentsToChilds(boolean isFirstPage, String lastDocId) {

		// Variable for return value:
		String returnValue = null;

		SolrDocumentList resultDocList = relationHelper.getCurrentlyIndexedChildRecords(isFirstPage, lastDocId);

		String newLastDocId = resultDocList.get(resultDocList.size()-1).getFieldValue("id").toString();

		for (SolrDocument childRecord : resultDocList) {

			counter = counter + 1;

			String docId = (childRecord.getFieldValue("id") != null) ? childRecord.getFieldValue("id").toString() : null;
			Set<String> parentAcs = relationHelper.getDedupParentAcsFromSingleChild(childRecord);
			
			List<SolrDocument> parentRecords = relationHelper.getParentRecords(parentAcs);

			if (parentRecords != null && !parentRecords.isEmpty()) {
				
				for (SolrDocument parentRecord : parentRecords) {
					
					String parentRecordSys = (parentRecord.getFieldValue("id") != null) ? parentRecord.getFieldValue("id").toString() : "0";
					String parentRecordTitle = (parentRecord.getFieldValue("title") != null) ? parentRecord.getFieldValue("title").toString() : "0";

					// Prepare child record for atomic updates:
					SolrInputDocument parentToChildDoc = null;
					parentToChildDoc = new SolrInputDocument();
					parentToChildDoc.setField("id", docId);

					// Add values to child record with atomic update:
					Map<String, String> mapParentRecordSys = new HashMap<String, String>();
					mapParentRecordSys.put("add", parentRecordSys);
					parentToChildDoc.setField("parentSYS_str_mv", mapParentRecordSys);

					Map<String, String> mapParentRecordTitle = new HashMap<String, String>();
					mapParentRecordTitle.put("add", parentRecordTitle);
					parentToChildDoc.setField("parentTitle_str_mv", mapParentRecordTitle);

					// Add all values of MU child record to MH parent record:
					docsForAtomicUpdates.add(parentToChildDoc);
				}
			}
			
			AkImporterHelper.print(this.print, "\nLinking parent(s) to it's child(s). Processing record no " + counter  + " of " + noOfDocs);

			// If the last document of the solr result page is reached, build a new filter query so that we can iterate over the next result page:
			if (docId.equals(newLastDocId)) {
				returnValue = docId;
			}
		}
		return returnValue;
	}
}
