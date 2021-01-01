package managers;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;

import org.apache.commons.lang3.StringUtils;

import auxiliar.LocalUserInfo;
import auxiliar.NextStep;
import constants.KConstants;
import constants.KUrls;
import fileConverters.CSVWriter;
import fileConverters.JSONFlattener;
import fileConverters.SQLtoCSVConverter;
import fileConverters.XLSXToCSVConverter;
import fileConverters.XLStoCSVConverter;
import filesAndPaths.FilesAndPathsException;
import filesAndPaths.ProgramFileInfo;
import programAnalysis.ProgramAnalysis;
import storeHouse.CacheStoreHouseCleaner;

public class FilesManager extends AbstractManager {
	
	public FilesManager() {
	}

	@Override
	public String methodToInvokeIfMethodRequestedIsNotAvailable() {
		return "list";
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void list() throws Exception {
		ProgramFileInfo[] filesList = FilesManagerAux.list(requestStoreHouse);
		resultsStoreHouse.setFilesList(filesList);

		// Forward to the jsp page.
		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.ListPage, ""));
	}

	public void listMyFiles() throws Exception {
		ProgramFileInfo[] filesList = FilesManagerAux.listMyFiles(requestStoreHouse);
		resultsStoreHouse.setFilesList(filesList);

		String append = "";
		if(StringUtils.isNotBlank(requestStoreHouse.getRequestParameter("uploadedFile"))) {
			append = "?uploadedFile=" + requestStoreHouse.getRequestParameter("uploadedFile");
		}

		// Forward to the jsp page.
		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.ListPage, append));
	}

	public void uploadDiv() throws Exception {
		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.UploadDivPage, ""));
	}

	public void upload() throws Exception {

		String[] msgs = FilesManagerAux.uploadFileAux(requestStoreHouse, this);
		String append = "";
		for (int i = 0; i < msgs.length; i++) {
			if(msgs.length == 1 && StringUtils.startsWith(msgs[i], "filename::")) {
				append = "?uploadedFile=" + StringUtils.split(msgs[i], "::")[1];
				//remove msgs[i]
//				msgs = ArrayUtils.remove(msgs, i);
			}
			else if(!StringUtils.startsWith(msgs[i], "filename::")) {
				resultsStoreHouse.addResultMessage(msgs[i]);
			}
		}
		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.UploadPage, append));
	}

	public void download() throws Exception {

		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		// request.getParameter("filename");
		String browser_filename = programFileInfo.getProgramFileFullPath();

		File f = new File(programFileInfo.getProgramFileFullPath());
		int length = 0;
		ServletOutputStream op = requestStoreHouse.getResponse().getOutputStream();
		String mimetype = requestStoreHouse.getServletContext().getMimeType(programFileInfo.getProgramFileFullPath());

		//
		// Set the response and go!
		//
		//
		requestStoreHouse.getResponse().setContentType((mimetype != null) ? mimetype : "application/octet-stream");
		requestStoreHouse.getResponse().setContentLength((int) f.length());
		requestStoreHouse.getResponse().setHeader("Content-Disposition",
				"attachment; filename=\"" + browser_filename + "\"");

		//
		// Stream to the requester.
		//
		byte[] bbuf = new byte[KConstants.Communications.BUFSIZE];
		DataInputStream in = new DataInputStream(new FileInputStream(f));

		while ((in != null) && ((length = in.read(bbuf)) != -1)) {
			op.write(bbuf, 0, length);
		}

		in.close();
		op.flush();
		op.close();

		// We write the destiny. Do not look for a Next Step.
		setNextStep(null);
	}

	public void save() throws Exception // function that saves the fuzzy types
										// for the csv functions and creates .pl
										// file
	{
		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();

		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner())))
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot translate it.");
		else {
			String types = programFileInfo.getProgramFileFullPath();
			int i = 0;
			while (requestStoreHouse.getRequestParameter("type[" + i + "]") != "") {
				types += " " + requestStoreHouse.getRequestParameter("type[" + i + "]");
				i++;
			}
			Process process = Runtime.getRuntime().exec("ruby " + KConstants.PathsMgmt.rbScriptPath + " " + types);
			try {
				process.waitFor();

				String[] temp = programFileInfo.getFileName().split("\\.");

				int result = addDefaultSimilarity(programFileInfo, temp[0], localUserInfo);

				String msg = "Program file " + programFileInfo.getFileName() + " owned by "
						+ programFileInfo.getFileOwner() + " has NOT been updated. ";
				if (result == 0) {
					msg = "Program file " + programFileInfo.getFileName() + " owned by "
							+ programFileInfo.getFileOwner() + " has been updated. ";
					resultsStoreHouse.addResultMessage(msg);
					setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.SavePage, ""));
				}
				resultsStoreHouse.addResultMessage(msg);

			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}

		}

	}

	public void convertJson() throws Exception // function that convert the
												// fuzzy types for the json and
												// creates .pl file
	{
		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();

		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner())))
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot translate it.");
		else {
			List<Map<String, String>> flatJson = JSONFlattener
					.parseJson(new File(programFileInfo.getProgramFileFullPath()), "UTF-8");

			String[] temp = programFileInfo.getFileName().split("\\.");

			CSVWriter.writeToFile(CSVWriter.getCSV(flatJson, ","),
					programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv");

			String types = programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv";

			int i = 0;
			while (requestStoreHouse.getRequestParameter("type[" + i + "]") != "") {
				types += " " + requestStoreHouse.getRequestParameter("type[" + i + "]");
				i++;
			}
			Process process = Runtime.getRuntime().exec("ruby " + KConstants.PathsMgmt.rbScriptPath + " " + types);
			try {
				process.waitFor();
				File tempFile = new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv");
				tempFile.delete();

				int result = addDefaultSimilarity(programFileInfo, temp[0], localUserInfo);

				String msg = "Program file " + programFileInfo.getFileName() + " owned by "
						+ programFileInfo.getFileOwner() + " has NOT been updated. ";
				if (result == 0) {
					msg = "Program file " + programFileInfo.getFileName() + " owned by "
							+ programFileInfo.getFileOwner() + " has been updated. ";
					resultsStoreHouse.addResultMessage(msg);
					setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.SaveJsonPage, ""));
				}
				resultsStoreHouse.addResultMessage(msg);

			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}

		}

	}

	public int addDefaultSimilarity(ProgramFileInfo programFileInfo, String filename, LocalUserInfo localUserInfo)
			throws Exception {
		ProgramFileInfo updatedProgramFileInfo = new ProgramFileInfo(programFileInfo.getFileOwner(),
				filename.toLowerCase() + ".pl");
		ProgramAnalysis programAnalized = ProgramAnalysis.getProgramAnalysisClass(updatedProgramFileInfo);

		int result = -1;

		result = programAnalized.updateProgramFileForDefaultSimiarity(localUserInfo, "", "_");

		return result;

	}

	public void convertXLSX() throws Exception // function that convert the
												// fuzzy types for the xlsx and
												// creates .pl file
	{
		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();
		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner())))
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot translate it.");
		else {
			String[] temp = programFileInfo.getFileName().split("\\.");

			XLSXToCSVConverter.convert(new File(programFileInfo.getProgramFileFullPath()),
					new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv"), ",");

			String types = programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv";

			int i = 0;
			while (requestStoreHouse.getRequestParameter("type[" + i + "]") != "") {
				types += " " + requestStoreHouse.getRequestParameter("type[" + i + "]");
				i++;
			}
			Process process = Runtime.getRuntime().exec("ruby " + KConstants.PathsMgmt.rbScriptPath + " " + types);
			try {
				process.waitFor();
				File tempFile = new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv");
				tempFile.delete();

				int result = addDefaultSimilarity(programFileInfo, temp[0], localUserInfo);

				String msg = "Program file " + programFileInfo.getFileName() + " owned by "
						+ programFileInfo.getFileOwner() + " has NOT been updated. ";
				if (result == 0) {
					msg = "Program file " + programFileInfo.getFileName() + " owned by "
							+ programFileInfo.getFileOwner() + " has been updated. ";
					resultsStoreHouse.addResultMessage(msg);
					setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.SaveXLSXPage, ""));
				}
				resultsStoreHouse.addResultMessage(msg);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}

		}

	}

	public void convertXLS() throws Exception // function that convert the fuzzy
												// types for the xls and creates
												// .pl file
	{
		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();
		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner())))
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot translate it.");
		else {
			String[] temp = programFileInfo.getFileName().split("\\.");

			XLStoCSVConverter.convert(new File(programFileInfo.getProgramFileFullPath()),
					new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv"), ",");

			String types = programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv";

			int i = 0;
			while (requestStoreHouse.getRequestParameter("type[" + i + "]") != "") {
				types += " " + requestStoreHouse.getRequestParameter("type[" + i + "]");
				i++;
			}
			Process process = Runtime.getRuntime().exec("ruby " + KConstants.PathsMgmt.rbScriptPath + " " + types);
			try {
				process.waitFor();
				File tempFile = new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv");
				tempFile.delete();

				int result = addDefaultSimilarity(programFileInfo, temp[0], localUserInfo);

				String msg = "Program file " + programFileInfo.getFileName() + " owned by "
						+ programFileInfo.getFileOwner() + " has NOT been updated. ";
				if (result == 0) {
					msg = "Program file " + programFileInfo.getFileName() + " owned by "
							+ programFileInfo.getFileOwner() + " has been updated. ";
					resultsStoreHouse.addResultMessage(msg);
					setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.SaveXLSPage, ""));
				}
				resultsStoreHouse.addResultMessage(msg);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}

		}

	}

	public void convertSQL() throws Exception // function that convert the fuzzy
												// types for the sql and creates
												// .pl file
	{
		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();
		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner())))
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot translate it.");
		else {
			String[] temp = programFileInfo.getFileName().split("\\.");

			SQLtoCSVConverter.sqlToCSVConversion(new File(programFileInfo.getProgramFileFullPath()),
					new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv"), ",");

			String types = programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv";

			int i = 0;
			while (requestStoreHouse.getRequestParameter("type[" + i + "]") != "") {
				types += " " + requestStoreHouse.getRequestParameter("type[" + i + "]");
				i++;
			}
			Process process = Runtime.getRuntime().exec("ruby " + KConstants.PathsMgmt.rbScriptPath + " " + types);
			try {
				process.waitFor();
				File tempFile = new File(programFileInfo.getProgramFileFolderFullPath() + "/" + temp[0] + ".csv");
				tempFile.delete();

				int result = addDefaultSimilarity(programFileInfo, temp[0], localUserInfo);

				String msg = "Program file " + programFileInfo.getFileName() + " owned by "
						+ programFileInfo.getFileOwner() + " has NOT been updated. ";
				if (result == 0) {
					msg = "Program file " + programFileInfo.getFileName() + " owned by "
							+ programFileInfo.getFileOwner() + " has been updated. ";
					resultsStoreHouse.addResultMessage(msg);
					setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.SaveSQLPage, ""));
				}
				resultsStoreHouse.addResultMessage(msg);

			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}

		}

	}

	public void remove() throws Exception {

		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();

		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner()))) {
			resultsStoreHouse.addResultMessage("You do not own the program file. So, you cannot remove it.");
		} else {
			String result = programFileInfo.remove();
			if ((result != null) && (result.length() > 0)) {
				resultsStoreHouse.addResultMessage(result);
			}
			CacheStoreHouseCleaner.clean(programFileInfo);
		}

		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.RemovePage, ""));
	}

	public void changeState() throws Exception {

		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();

		if (!(localUserInfo.getLocalUserName().equals(programFileInfo.getFileOwner()))) {
			resultsStoreHouse
					.addResultMessage("You do not own the program file. So, you cannot change its sharing state.");
		} else {
			FilesManagerAux.changeSharingState(programFileInfo);
		}

		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.ChangeStatePage, ""));
	}

	public void view() throws Exception {

		ProgramFileInfo programFileInfo = requestStoreHouse.getProgramFileInfo();
		LocalUserInfo localUserInfo = requestStoreHouse.getSession().getLocalUserInfo();

		String[] fileContents = null;

		try {
			if (programFileInfo.getFileName().endsWith(".csv")) {
				String[] aux = programFileInfo.getFileContents(localUserInfo);
				fileContents = new String[aux.length + 3];
				fileContents[0] = null;
				fileContents[1] = programFileInfo.getFileName();
				fileContents[2] = programFileInfo.getFileOwner();
				for (int i = 0; i < aux.length; i++)
					fileContents[i + 3] = aux[i];
			} else
				fileContents = programFileInfo.getFileContents(localUserInfo);
		} catch (FilesAndPathsException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
			throw new FilesManagerException(e.getMessage());
		}
		resultsStoreHouse.setFileContents(fileContents);

		setNextStep(new NextStep(KConstants.NextStep.forward_to, KUrls.Files.ViewPage, ""));
	}

}
