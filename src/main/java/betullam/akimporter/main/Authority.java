/**
 * Importing authority records (GND) to Solr index.
 *
 * Copyright (C) AK Bibliothek Wien 2016, Michael Birkner
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

package main.java.betullam.akimporter.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.solr.client.solrj.impl.HttpSolrServer;

import main.java.betullam.akimporter.solrmab.Index;
import main.java.betullam.akimporter.solrmab.SolrMabHelper;
import main.java.betullam.akimporter.solrmab.relations.AuthorityFlag;
import main.java.betullam.akimporter.solrmab.relations.AuthorityIntegrate;

public class Authority {

	private boolean flagOnly;
	private String pathToAuthFile = null;
	private String[] pathsToAuthFiles = null;
	private boolean useDefaultAuthProperties = true;
	private String pathToAuthProperties = null;
	private String pathToTranslationFiles = null;
	private String solrServerAuth = null;
	private String solrServerBiblio = null;
	private String timeStamp = null;
	private boolean print = false;
	private boolean optimize = false;
	private SolrMabHelper smHelper = new SolrMabHelper();

	/**
	 * Constructor for setting some variables.
	 * 
	 * @param flagOnly						boolean indicating if the authority data should be indexed or if only the flag of existance should be set.
	 * @param pathToAuthFile				String indicating the path to an authority file (e. g. /path/to/persons.xml)
	 * @param useDefaultAuthProperties		boolean indicating if the default authority properties for indexing should be used
	 * @param pathToCustomAuthProperties	String indicating the path to a custom .properties file (e. g. /path/to/custom_authority.properties)
	 * @param solrServerAuth				String indicating the URL incl. core name of the Solr authority index (e. g. http://localhost:8080/solr/authority)
	 * @param solrServerBiblio				String indicating the URL incl. core name of the Solr bibliographic index (e. g. http://localhost:8080/solr/biblio)
	 * @param timeStamp						Current unix time stamp as a String or null
	 * @param print							boolean indicating whether to print status messages or not
	 * @param optimize						boolean indicating whether to optimize the solr index not
	 */
	public Authority(boolean flagOnly, String pathToAuthFile, boolean useDefaultAuthProperties, String pathToCustomAuthProperties, String solrServerAuth, String solrServerBiblio, String timeStamp, boolean print, boolean optimize) {
		this.flagOnly = flagOnly;
		this.pathToAuthFile = pathToAuthFile;
		this.pathsToAuthFiles = this.pathToAuthFile.split(",");
		this.useDefaultAuthProperties = useDefaultAuthProperties;
		if (this.useDefaultAuthProperties) {
			//this.pathToAuthProperties = "/betullam/akimporter/resources/authority.properties";
			//this.pathToTranslationFiles = "/betullam/akimporter/resources";
			this.pathToAuthProperties = "/authority.properties";
			this.pathToTranslationFiles = "/";
		} else {
			this.pathToAuthProperties = pathToCustomAuthProperties;
			this.pathToTranslationFiles = new File(this.pathToAuthProperties).getParent();

			// It the translation files, that are defined in the custom authority properties file, do not exist
			// (they have to be in the same directory), that give an appropriate message:
			boolean areTranslationFilesOk = Main.translationFilesExist(this.pathToAuthProperties, this.pathToTranslationFiles);
			if (areTranslationFilesOk == false) {
				System.err.println("Stopping import process due to error with translation files.");
			}
		}
		this.solrServerAuth = solrServerAuth;
		this.solrServerBiblio = solrServerBiblio;
		this.timeStamp = timeStamp;
		this.print = print;
		this.optimize = optimize;
	}

	/**
	 * Starting the index process for authority records.
	 * If only the flag of existance should be set, the data itself will not be indexed.
	 * @return	true if the index process was sucessful, false otherwise
	 */
	public boolean indexAuthority() {
		boolean returnValue = false;

		HttpSolrServer solrServerAuth = new HttpSolrServer(this.solrServerAuth);
		if (this.timeStamp == null) {
			this.timeStamp = String.valueOf(new Date().getTime());
		}

		if (this.flagOnly) {
			// TEST DEDUP - BEGIN
			//this.smHelper.dedupSolrMultivaluedField(solrServerAuth, "gndId035_str_mv", true, false); // Dedup Solr field
			// TEST DEDUP - END
			
			HttpSolrServer solrServerBiblio = new HttpSolrServer(this.solrServerBiblio);
			AuthorityFlag af = new AuthorityFlag(solrServerBiblio, solrServerAuth, null, print);
			af.setFlagOfExistance();
			this.smHelper.print(this.print, "\nDone setting flag of existance to authority records.");
			returnValue = true;
		} else {
			boolean isIndexingSuccessful = false;
			for (String pathToAuthFile : this.pathsToAuthFiles) {
				Index index = new Index (
						pathToAuthFile.trim(),
						solrServerAuth,
						this.useDefaultAuthProperties,
						this.pathToAuthProperties,
						this.pathToTranslationFiles,
						this.timeStamp,
						this.optimize,
						this.print
				);
				
				isIndexingSuccessful = index.isIndexingSuccessful();
				if (!isIndexingSuccessful) {
					break;
				}
			}

			if(isIndexingSuccessful) {
				HttpSolrServer solrServerBiblio = new HttpSolrServer(this.solrServerBiblio);
				
				// TEST DEDUP - BEGIN
				//this.smHelper.dedupSolrMultivaluedField(solrServerAuth, "gndId035_str_mv", true, false); // Dedup Solr field
				// TEST DEDUP - END
				
				AuthorityFlag af = new AuthorityFlag(solrServerBiblio, solrServerAuth, null, print);
				af.setFlagOfExistance();
				this.smHelper.print(this.print, "\nDone indexing authority records.");
				returnValue = true;
			} else {
				System.err.println("Error indexing authority records!");
				returnValue = false;
			}
			
		}

		return returnValue;
	}
	
	
	/**
	 * Integrates the authority data to the bibliographic data.
	 * If only the flagOnly is true (see class constructor)the data itself will not be indexed. Then, only the
	 * flag of existance will be set and the data integration will be performed.
	 * 
	 * @param entity	The entity of GND records that should be integrated (e. g. Person, Corporation, etc.)
	 * @return bollean	Indicates if the index process was successful
	 */
	public boolean integrateAuthority(String entity) {

		
		boolean returnValue = false;

		HttpSolrServer solrServerAuth = new HttpSolrServer(this.solrServerAuth);
		if (this.timeStamp == null) {
			this.timeStamp = String.valueOf(new Date().getTime());
		}

		if (this.flagOnly) {
			HttpSolrServer solrServerBiblio = new HttpSolrServer(this.solrServerBiblio);
			
			// Set flag of existance
			AuthorityFlag af = new AuthorityFlag(solrServerBiblio, solrServerAuth, null, print);
			af.setFlagOfExistance();
			
			// Integrate:
			AuthorityIntegrate ai = new AuthorityIntegrate(solrServerBiblio, solrServerAuth, null, print);
			ai.integrateAuthorityRecords(entity);
			
			// De-duplicate authority fields in biblio records:
			List<String> fieldsForDedup = new ArrayList<String>();
			fieldsForDedup.add("authUseForAdditionsPerson_txt_mv");
			fieldsForDedup.add("authUseForPerson_txt_mv");
			fieldsForDedup.add("authHeadingAdditionsPerson_txt_mv");
			fieldsForDedup.add("authHeadingPerson_txt_mv");
			fieldsForDedup.add("authOtherAdditionsPerson_txt_mv");
			this.smHelper.dedupSolrMultivaluedField(solrServerBiblio, fieldsForDedup, true, false);
			
			
			this.smHelper.print(this.print, "\nDone integrating authority records to bibliographic records.");
			returnValue = true;
		} else {
			boolean isIndexingSuccessful = false;
			for (String pathToAuthFile : this.pathsToAuthFiles) {
				Index index = new Index (
						pathToAuthFile.trim(),
						solrServerAuth,
						this.useDefaultAuthProperties,
						this.pathToAuthProperties,
						this.pathToTranslationFiles,
						this.timeStamp,
						this.optimize,
						this.print
				);
				
				isIndexingSuccessful = index.isIndexingSuccessful();
				if (!isIndexingSuccessful) {
					break;
				}
			}
			
			if(isIndexingSuccessful) {
				HttpSolrServer solrServerBiblio = new HttpSolrServer(this.solrServerBiblio);
				
				// Set flag of existance
				AuthorityFlag af = new AuthorityFlag(solrServerBiblio, solrServerAuth, null, print);
				af.setFlagOfExistance();
				
				// Integrate:
				AuthorityIntegrate ai = new AuthorityIntegrate(solrServerBiblio, solrServerAuth, null, print);
				ai.integrateAuthorityRecords(entity);
				
				// De-duplicate authority fields in biblio records:
				List<String> fieldsForDedup = new ArrayList<String>();
				fieldsForDedup.add("authUseForAdditionsPerson_txt_mv");
				fieldsForDedup.add("authUseForPerson_txt_mv");
				fieldsForDedup.add("authHeadingAdditionsPerson_txt_mv");
				fieldsForDedup.add("authHeadingPerson_txt_mv");
				fieldsForDedup.add("authOtherAdditionsPerson_txt_mv");
				this.smHelper.dedupSolrMultivaluedField(solrServerBiblio, fieldsForDedup, true, false);
				
				
				this.smHelper.print(this.print, "\nDone indexing and integrating authority records to bibliographic records.");
				
				returnValue = true;
			} else {
				System.err.println("Error indexing authority records!");
				returnValue = false;
			}
		}

		return returnValue;
		
	}
}