package betullam.akimporter.main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer.RemoteSolrException;

import betullam.akimporter.solrmab2.SolrMab;
import betullam.akimporter.updater.Updater;
import betullam.xmlhelper.XmlCleaner;
import betullam.xmlhelper.XmlMerger;
import betullam.xmlhelper.XmlValidator;

public class Main {

	static boolean isIndexerTest = false;
	static boolean isUpdate = false;
	static boolean isUpdateSuccessful = false;
	static boolean isIndexingOnly = false;
	static boolean isLinkingOnly = false;
	static boolean isReIndexOngoing = false;
	//static boolean isReIndexAll = false;
	static String timeStamp = "";

	// 1
	static Scanner scanner;
	static String typeOfDataset;
	static String pathToMabXmlFile;
	static String isValidationOk;
	static boolean hasValidationPassed;
	static String isXmlCleanOk;
	static String solrServerAddress;
	static String useDefaultMabPropertiesFile;
	static String pathToMabPropertiesFile;
	static String directoryOfTranslationFiles;
	static boolean areTranslationFilesOk;
	static String propertiesFileInfo;
	static boolean useDefaultMabProperties;
	static String isIndexingOk;
	static boolean isIndexingSuccessful;

	// 2
	static String pathToMultipleXmlFolder;
	static String isMergeOk;
	static String pathToMergedFile;
	static boolean isMergingSuccessful;



	/**
	 * TESTANGABEN:
	 * /home/mbirkner/AkFind/a.xml
	 * /home/mbirkner/AkFind/a_clean.xml
	 * http://localhost:8080/solr/akw
	 * /home/mbirkner/AkFind/mab.properties
	 * 
	 * /home/mbirkner/AkFind/MabData/lbo_merged.xml
	 * 
	 */

	public static void main(String[] args) {

		timeStamp = String.valueOf(new Date().getTime());
		scanner = new Scanner(System.in);
		BasicConfigurator.configure(); // Log-Output (avoid error message "log4j - No appenders could be found for logger")
		Logger.getRootLogger().setLevel(Level.WARN); // Set log4j-output to "warn" (avoid very long logs in console)


		if (args.length > 0) {
			isIndexerTest = (args[0].equals("-t")) ? true : false; // ONLY FOR TESTING PURPOSES
			isUpdate = (args[0].equals("-u")) ? true : false; // Running update
			isIndexingOnly = (args[0].equals("-i")) ? true : false; // Index only without linking parent and child volumes
			isLinkingOnly = (args[0].equals("-l")) ? true : false; // Link only parent and child volumes
			isReIndexOngoing = (args[0].equals("-ro")) ? true : false; // Reindex all ongoing data deliveries from "merged" folder in the right order.
		}


		if (isUpdate) {
			String remotePath = args[1];
			String localPath = args[2];
			String host = args[3];
			int port = Integer.valueOf(args[4]);
			String user = args[5];
			String password = args[6];
			String solrAddress = args[7];
			String defaultSolrMab = args[8];
			boolean showMessages = Boolean.valueOf(args[9]);

			Updater updater = new Updater(timeStamp);
			isUpdateSuccessful = updater.update(remotePath, localPath, host, port, user, password, solrAddress, defaultSolrMab, showMessages);
			return;
		}


		if (isIndexerTest) {
			typeOfDataset = args[1]; 
			pathToMabXmlFile = (typeOfDataset.equals("1"))? args[2] : null; // 1
			pathToMultipleXmlFolder = (typeOfDataset.equals("2")) ? args[2] : null; // 2
			pathToMergedFile = (typeOfDataset.equals("2")) ? args[3] : null;
			isMergeOk = "J"; // 2
			isValidationOk = "J"; // 1 + 2
			isXmlCleanOk = "J"; // 1 + 2
			solrServerAddress = (typeOfDataset.equals("1")) ? args[3] : args[4]; // 1 + 2
			useDefaultMabPropertiesFile = (typeOfDataset.equals("1")) ? args[4] : args[5]; // 1 + 2
			pathToMabPropertiesFile = (useDefaultMabPropertiesFile.equals("N") && typeOfDataset.equals("1")) ? args[5] : null; // 1 + 2
			pathToMabPropertiesFile = (useDefaultMabPropertiesFile.equals("N") && typeOfDataset.equals("2")) ? args[6] : null; // 1 + 2
			isIndexingOk = "J";
			isIndexingOnly = true; // 1 + 2
		}

		if (isLinkingOnly) {
			solrServerAddress = getUserInput("Geben Sie die Solr-Serveradresse (URL) inkl. Core-Name ein (z. B. http://localhost:8080/solr/corename)", "solrPing", scanner);
			SolrMab sm = new SolrMab(null, true);
			sm.startIndexing(null, solrServerAddress, null, null, false, false, true);
			return;
		}


		// TODO: REINDEX ALL ONGOING DATA DELIVERIES FROM "MERGED" FOLDER:
		if (isReIndexOngoing) {
			String pathToMergedDir = getUserInput("\nWie lautet der Pfad zur \"merged\" Ordner?\n Beispiel: /home/username/datenlieferungen/merged)?", "directoryExists", scanner);

			String isValidationOk = getUserInput("\nDie XML-Dateien müssen geprüft werden. Dies kann eine Weile dauern. Die Original-Daten werden nicht geändert. "
					+ "Wollen Sie fortfahren? Falls nicht, wird der gesamte Vorgang abgebrochen!"
					+ "\n J = Ja, fortfahren\n U = Überspringen\n N = Nein, abbrechen", "J, U, N", scanner);

			if (isValidationOk.equals("J") || isValidationOk.equals("U")) {

				if (isValidationOk.equals("J")) {
					System.out.println("\nStarte Validierung. Bitte um etwas Geduld ...");

					File fPathToMergedDir = new File(pathToMergedDir);
					
					List<File> fileList = (List<File>)FileUtils.listFiles(fPathToMergedDir, new String[] {"xml"}, true); // Get all xml-files recursively
					Collections.sort(fileList); // Sort oldest to newest
					
					XmlValidator bxh = new XmlValidator();
					
					for (File file : fileList) {
						hasValidationPassed = bxh.validateXML(file.getAbsolutePath());
						
						if (hasValidationPassed) {
							System.out.println(file.getName() + " is valid.");
						} else {
							System.out.println("Fehler in Datei " + file.getName() + ". Import-Vorgang wurde gestoppt.");
							return;
						}
					}
				}
				
			}
			
			return;
		}


		// NORMAL IMPORT PROCESS STARTS HERE

		if (!isIndexerTest) {
			typeOfDataset = getUserInput("\nWie liegt ihr Datenbestand vor?\n 1 = eine große XML-Datei\n 2 = viele einzelne XML-Dateien)?", "1, 2", scanner);
		}

		if (typeOfDataset.equals("1")) { // We have one big XML file
			if (!isIndexerTest) {
				pathToMabXmlFile = getUserInput("\nWie lautet der Pfad zur XML-Datei?\n Beispiel: /home/username/dateiname.xml)?", "fileExists", scanner);
			}

		} else if (typeOfDataset.equals("2")) { // We have multiple smaller XML files - we need to merge them!
			if (!isIndexerTest) {
				pathToMultipleXmlFolder = getUserInput("\nWie lautet der Pfad zum Ordner mit den einzelnen XML-Dateien?\n Beispiel: /home/username/xmldateien)?", "directoryExists", scanner);
			}

			if (!isIndexerTest) {
				isMergeOk = getUserInput("\nDie XML-Dateien müssen nun in eine einzige XML-Datei zusammengeführt werden."
						+ " Die Original-Daten werden nicht geändert. Wollen Sie fortfahren? Falls nicht, wird der gesamte"
						+ " Import-Vorgang abgebrochen! "
						+ "\n J = Ja, fortfahren\n N = Nein, abbrechen", "J, N", scanner);
			}

			if (isMergeOk.equals("J")) {

				if (!isIndexerTest) {
					pathToMergedFile = getUserInput("\nGeben Sie an, wo die Datei mit den zusammengeführten Daten gespeichert werden"
							+ " sollen. Geben Sie dazu einen Pfad inkl. Dateiname und der Endung \".xml\" an,"
							+ " z. B.: /home/benutzer/meinedatei.xml. Beachten Sie, dass Sie am angegebenen Ort Schreibberechigungen"
							+ " haben müssen und es NICHT der gleiche Ort sein darf, in dem die einzelnen XML-Dateien liegen.", "newFile", scanner);
				}

				// Start XML merging:
				XmlMerger xmlm = new XmlMerger();
				isMergingSuccessful = xmlm.mergeElementNodes(pathToMultipleXmlFolder, pathToMergedFile, "collection", "record", 1);

				if (isMergingSuccessful) {
					pathToMabXmlFile = pathToMergedFile;
					System.out.println("\nDatenzusammenführung erfolgreich abgeschlossen.\n");
				} else {
					System.err.println("\nFehler bei der Datenzusammenführung! Vorgang abgebrochen.\n");
					return;
				}
			} else {
				System.out.println("\nImport-Vorgang auf Benutzerwunsch abgebrochen.\n");
				return;
			}
		}


		if (!isIndexerTest) {
			isValidationOk = getUserInput("\nDie XML-Datei muss geprüft werden. Dies kann eine Weile dauern. Die Original-Daten werden nicht geändert. "
					+ "Wollen Sie fortfahren? Falls nicht, wird der gesamte Vorgang abgebrochen! "
					+ "\n J = Ja, fortfahren\n U = Überspringen\n N = Nein, abbrechen", "J, U, N", scanner);
		}

		if (isValidationOk.equals("J") || isValidationOk.equals("U")) {

			if (isValidationOk.equals("J")) {
				System.out.println("\nStarte Validierung. Bitte um etwas Geduld ...");
				XmlValidator bxh = new XmlValidator();
				hasValidationPassed = bxh.validateXML(pathToMabXmlFile);

				while (hasValidationPassed == false) {
					System.out.println("\nProblem in der XML Datei gefunden!");
					if (!isIndexerTest) {
						isXmlCleanOk = getUserInput("\nWollen Sie eine Datenbereinigung durchführen? "
								+ "Die Originaldaten werden nicht verändert. "
								+ "Dieser Vorgang kann je nach Datenmenge länger dauern. "
								+ "Wenn Sie keine Datenbereinigung durchführen, wird der Vorgang abgebrochen."
								+ "\n J = Ja, Datenbereinigung durchführen\n N = Nein, Import-Vorgang abbrechen", "J, N", scanner);
					}
					if (isXmlCleanOk.equals("J")) {
						// Start cleaning XML
						XmlCleaner xmlc = new XmlCleaner();
						boolean cleaningProcessDone = xmlc.cleanXml(pathToMabXmlFile);
						boolean isNewXmlFileClean = false;
						if (cleaningProcessDone == true) {
							pathToMabXmlFile = xmlc.getCleanedFile();
							isNewXmlFileClean = bxh.validateXML(xmlc.getCleanedFile());
							if (isNewXmlFileClean == false) {
								System.out.println("\nDaten konnten nicht bereinigt werden! Import-Vorgang wurde abgebrochen.");
								return;
							} else {
								hasValidationPassed = true;
							}
						} else {
							System.out.println("\nProblem bei der Datenbereinigung! Möglicherweise haben Sie keine"
									+ " Schreibberechtigung für den Ordner, in den die bereinigte Datei geschrieben wird"
									+ " (der gleiche wie die Ausgangsdatei \"" + pathToMabXmlFile + "\").");
							return;
						}
					} else {
						System.out.println("\nImport-Vorgang auf Benutzerwunsch abgebrochen!");
						return;
					}
				}
			} else {

				hasValidationPassed = true;
			}


			if (hasValidationPassed) {
				if (isValidationOk.equals("J")) {
					System.out.println("\nValidierung war erfolgreich. Die Daten sind nun bereit für die Indexierung.\n");
				}
				if (isValidationOk.equals("U")) {
					System.out.println("\nValidierung übersprungen!");
				}

				if (!isIndexerTest) {
					solrServerAddress = getUserInput("Geben Sie die Solr-Serveradresse (URL) inkl. Core-Name ein (z. B. http://localhost:8080/solr/corename)", "solrPing", scanner);
				}
				if (!isIndexerTest) {
					useDefaultMabPropertiesFile = getUserInput("\nWollen Sie die \"mab.properties\" Datei in der Standardkonfiguration verwenden? "
							+ "Wenn Sie dies nicht wollen, können Sie anschließend einen Pfad zu einer eigenen .properties-Datei angeben."
							+ "\n J = Ja, Standard verwenden\n N = Nein, Standard nicht verwenden", "J, N", scanner);
				}
				if (useDefaultMabPropertiesFile.equals("J")) {
					useDefaultMabProperties = true;
					pathToMabPropertiesFile = Main.class.getResource("/betullam/akimporter/resources/mab.properties").getFile();
					directoryOfTranslationFiles = Main.class.getResource("/betullam/akimporter/resources").getPath();
					propertiesFileInfo = "Standard mab.properties Datei verwenden";
				} else {
					useDefaultMabProperties = false;
					if (!isIndexerTest) {
						pathToMabPropertiesFile = getUserInput("\nBitte geben den Pfad zu Ihrer eigenen .properties-Datei an (z. B. /home/username/meine.properties). Beachten Sie, dass die Dateiendung wirklich \".properties\" sein muss!", "propertiesExists", scanner);
					}
					propertiesFileInfo = "Eigene .properties Datei verwenden: " + pathToMabPropertiesFile;

					directoryOfTranslationFiles = new File(pathToMabPropertiesFile).getParent();
					areTranslationFilesOk = translationFilesExist(pathToMabPropertiesFile, directoryOfTranslationFiles);

					// It the translation files, that are defined in the custom MAB properties file, do not exist
					// (they have to be in the same directory), that give an appropriate message:
					while (areTranslationFilesOk == false) {
						scanner.nextLine();
						areTranslationFilesOk = translationFilesExist(pathToMabPropertiesFile ,directoryOfTranslationFiles);
					}


				}

				if (!isIndexerTest) {
					isIndexingOk = getUserInput("\nAlles ist nun bereit. Hier noch einmal Ihre Angaben:"
							+ "\n Daten-Datei:\t" + pathToMabXmlFile
							+ "\n Solr Server:\t" + solrServerAddress
							+ "\n .properties:\t" + propertiesFileInfo
							+ "\n\nWollen Sie den Import-Vorgang nun beginnen?"
							+ "\nACHTUNG: Ja nach Datenmenge und Leistung des Computers kann dieser Vorgang lange dauern!"
							+ " \n J = Ja, Import-Vorgang beginnen\n N = Nein, Import-Vorgang abbrechen", "J, N", scanner);
				}

				if (isIndexingOk.equals("J")) {
					SolrMab sm = new SolrMab(timeStamp, true);
					isIndexingSuccessful = sm.startIndexing(pathToMabXmlFile, solrServerAddress, pathToMabPropertiesFile, directoryOfTranslationFiles, useDefaultMabProperties, isIndexingOnly, isLinkingOnly);

					if (isIndexingSuccessful == true) {
						System.out.println("\nImport-Vorgang erfolgreich abgeschlossen.\n");
					} else {
						System.err.println("\nFehler beim Import-Vorgang!\n");
						return;
					}
				} else {
					System.out.println("\nImport-Vorgang auf Benutzerwunsch abgebrochen.\n");
					return;
				}

			}
		} else {
			System.out.println("\nImport-Vorgang auf Benutzerwunsch abgebrochen!");
			return;
		}

		scanner.close();
	}




	/**
	 * Ask for user input.
	 * You can define a comma-separated String as possible anwers. If the user-input is not one of them, the questions will be asked again until a
	 * correct answer is given. If you ask for a path or file and want to check if it exists, use "fileExists" or "directoryExists" for possibleAnswers.
	 * If you want to check if a .properties file exists, use "propertiesExists" for possibleAnswers. If you want to create a new file, use "newFile" for
	 * possible Answers. You can also ask for a Solr server URL and ping it (check if it is running) by using "solrPing" for possibleAnswers.
	 * If you don't need to validate an answer, use null.
	 * 
	 * @param question			A question you want the user to answer as a String.
	 * @param possibleAnswers	A comma-separated String with possible answers: "fileExists", "directoryExists", "propertiesExists", "newFile", "solrPing" or null (see above explanation).
	 * @param scanner			A Scanner(System.in) object.
	 * @return
	 */
	public static String getUserInput(String question, String possibleAnswers, Scanner scanner) {
		boolean isValidAnswer = false;
		String userinput = "";
		List<String> lstPossibleAnswers = new ArrayList<String>();

		System.out.println(question);

		while (isValidAnswer == false) {
			userinput = scanner.nextLine();
			File file;
			if (possibleAnswers == null) {
				isValidAnswer = true;
			} else {
				if (possibleAnswers.equals("fileExists")) { // Check if FILE exists
					file = new File(userinput);
					if (fileExists(file)) {
						if (canReadFile(file)) {
							isValidAnswer = true;
						} else {
							isValidAnswer = false;
							System.out.println("Keine Zugriffsberechtigung auf Datei! Bitte Datei mit Zugriffsberechtigung angeben oder Importer mit su-Rechten erneut starten!");
						}
					} else {
						isValidAnswer = false;
						System.out.println("Datei existiert nicht! Bitte erneut eingeben!");
					}
				} else if (possibleAnswers.equals("directoryExists")) { // Check if DIRECTORY exists
					file = new File(userinput);
					if (directoryExists(file)) {
						if (canReadFile(file)) {
							isValidAnswer = true;
						} else {
							isValidAnswer = false;
							System.out.println("Keine Zugriffsberechtigung auf Ordner! Bitte Ordner mit Zugriffsberechtigung angeben oder Importer mit su-Rechten erneut starten!");
						}
					} else {
						isValidAnswer = false;
						System.out.println("Ordner existiert nicht! Bitte erneut eingeben!");
					}
				} else if (possibleAnswers.equals("newFile")) { // Check if a new file can be created
					file = new File(userinput);
					if (canWriteToDirectory(file)) {
						if (fileExists(file)) {
							isValidAnswer = false;
							System.out.println("Diese Datei existiert bereits. Bitte anderen Dateinamen wählen!");
						} else {
							isValidAnswer = true;
						}
					} else {
						isValidAnswer = false;
						System.out.println("Keine Schreibberechtigung für den angegebenen Ort. Bitte anderen Ort wählen!");
					}

				} else if (possibleAnswers.equals("solrPing")) { // Check if Solr server is running
					if (isSolrserverRunning(userinput)) {
						isValidAnswer = true;
					} else {
						isValidAnswer = false;
						System.out.println("Solr-Server ist unter \"" + userinput + "\" nicht erreichbar! Bitte prüfen Sie ob der Server läuft und der angegebene Core existiert und geben Sie die URL erneut ein!");
					}
				} else if (possibleAnswers.equals("propertiesExists")) {
					file = new File(userinput);
					if (fileExists(file) && userinput.endsWith(".properties")) {
						if (canReadFile(file)) {
							isValidAnswer = true;
						} else {
							isValidAnswer = false;
							System.out.println("Keine Zugriffsberechtigung auf .properties-Datei! Bitte .properties-Datei mit Zugriffsberechtigung angeben oder Importer mit su-Rechten erneut starten!");
						}
					} else {
						isValidAnswer = false;
						System.out.println("Datei existiert nicht bzw. ist keine .properties-Datei! Bitte erneut eingeben!");
					}
				} else {
					lstPossibleAnswers = getPossibleAnswers(possibleAnswers);
					if (lstPossibleAnswers.contains(userinput)) {
						isValidAnswer = true;
					} else {
						isValidAnswer = false;
						System.out.println("Falsche Eingabe: " + userinput + " (mögliche Eingaben sind: " + lstPossibleAnswers.toString() + ")");
					}
				}
			}
		}

		return userinput.trim();
	}


	public static List<String> getPossibleAnswers(String commaSeparatedString) {
		List<String> possibleAnswers = new ArrayList<String>();

		String[] arrPossibleAnswers = commaSeparatedString.split(",");

		for (String possibleAnswer : arrPossibleAnswers) {
			possibleAnswers.add(possibleAnswer.trim());
		}

		return possibleAnswers;
	}


	public static boolean fileExists(File f) {
		boolean fileExists = false;
		fileExists = (f.exists() && f.isFile()) ? true : false;
		return fileExists;
	}

	public static boolean directoryExists(File f) {
		boolean directoryExists = false;
		directoryExists = (f.exists() && f.isDirectory()) ? true : false;
		return directoryExists;
	}

	public static boolean canReadFile(File f) {
		boolean canRead = false;
		canRead = (f.canRead()) ? true : false;
		return canRead;
	}

	public static boolean canWriteToDirectory(File f) {
		boolean canWrite = false;
		f = new File(f.getParent());
		canWrite = (f.canWrite()) ? true : false;
		return canWrite;
	}

	public static boolean isSolrserverRunning(String solrServerUrl) {
		boolean isSolrserverRunning = false;
		HttpSolrServer solrServer = new HttpSolrServer(solrServerUrl);
		int solrStatus;
		try {
			solrStatus = solrServer.ping().getStatus();
			if (solrStatus == 0) {
				isSolrserverRunning = true;
			}
		} catch (RemoteSolrException e) {
			return isSolrserverRunning;
		} catch (SolrServerException e) {
			return isSolrserverRunning;
		} catch (IOException e) {
			return isSolrserverRunning;
		} catch (IllegalStateException e) {
			return isSolrserverRunning;
		} catch (Exception e) {
			return isSolrserverRunning;
		}

		return isSolrserverRunning;
	}


	public static boolean translationFilesExist(String pathToMabPropertiesFile, String directoryOfTranslationFiles) {

		// Set some variables:
		Properties mabProperties = new Properties();
		boolean translationFilesExist = false;
		List<String> translationFilenames = new ArrayList<String>();

		// Load .properties file:
		BufferedInputStream propertiesInputStream;
		try {
			propertiesInputStream = new BufferedInputStream(new FileInputStream(pathToMabPropertiesFile));
			mabProperties.load(propertiesInputStream);
			propertiesInputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}


		// Loop through properties:
		for(String key : mabProperties.stringPropertyNames()) {

			// Create list:
			String strValues = mabProperties.getProperty(key);
			List<String> lstValues = new ArrayList<String>();
			lstValues.addAll(Arrays.asList(strValues.split("\\s*,\\s*")));

			// Get all translateValue fields:
			if (lstValues.contains("translateValue")) {
				if (lstValues.toString().contains(".properties")) { // Check if a .properties-File is indicated

					// Get the filename with the help of RegEx:
					Pattern patternPropFile = java.util.regex.Pattern.compile("[^\\s,;]*\\.properties"); // No (^) whitespaces (\\s), commas or semicolons (,;) before ".properties"-string.
					Matcher matcherPropFile = patternPropFile.matcher("");
					for(String lstValue : lstValues) {
						matcherPropFile.reset(lstValue);
						if(matcherPropFile.find()) {
							translationFilenames.add(matcherPropFile.group());
						}
					}
				}
			}
		}

		for (String translationFilename : translationFilenames) {
			String pathToTranslationFiles = directoryOfTranslationFiles + File.separator + translationFilename;
			if (fileExists(new File(pathToTranslationFiles)) == false) {
				System.out.println("Die in der .properties-Datei \"" + pathToMabPropertiesFile + "\" angegebene Translation-Datei \"" + translationFilename + "\" existiert im Verzeichnis \"" + directoryOfTranslationFiles + "\" nicht."
						+ "\nBitte stellen Sie sicher, dass die Translation-Datei dort existiert und drücken Sie die Eingabetaste um fortzufahren.");
				translationFilesExist = false;
				return translationFilesExist;
			} else {
				translationFilesExist = true;
			}
		}

		return translationFilesExist;
	}


}
