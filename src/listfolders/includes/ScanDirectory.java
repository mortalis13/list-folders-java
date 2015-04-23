package listfolders.includes;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import listfolders.ListFoldersMain;
import listfolders.includes.tree.DirNode;
import listfolders.includes.tree.FileNode;
import listfolders.includes.tree.TreeNode;

import com.google.gson.Gson;

public class ScanDirectory {
  Functions fun;
  
  String path;

  public String text;
  String markup;
  String json;

  ArrayList<String> textArray;
  ArrayList<String> markupArray;
  ArrayList<TreeNode> jsonArray;
  
  ArrayList<String> filterExt;
  ArrayList<String> excludeExt;
  ArrayList<String> filterDir;
  
  boolean doExportText;
  boolean doExportMarkup;
  boolean doExportTree;
  
  String exportName;

  String nl = "\n";
  String pad = "  ";
  String iconsPath="./lib/images/";
  
  String[] exts={                                       // sets of extensions for tree view icons (stored in lib/images)
    "chm", "css", "djvu", "dll", "doc", 
    "exe", "html", "iso", "js", "msi", 
    "pdf", "php", "psd", "rar", "txt", 
    "xls", "xml", "xpi", "zip",
  };
  
  String[] imageExts={
    "png", "gif", "jpg", "jpeg", "tiff", "bmp",
  };

  String[] musicExts={
    "mp3", "wav", "ogg", "alac", "flac",
  };

  String[] videoExts={
    "mkv", "flv", "vob", "avi", "wmv",
    "mov", "mp4", "mpg", "mpeg", "3gp",
  };

  public ScanDirectory() {
    String filterExtText, excludeExtText, filterDirText;
    fun=new Functions();
    
    textArray = new ArrayList<String>();
    markupArray = new ArrayList<String>();
    
    HashMap fields=fun.getFieldsMap();
    
    path=(String)fields.get("path");
    path=formatPath(path);
    
    filterExtText=(String)fields.get("filterExt");
    excludeExtText=(String)fields.get("excludeExt");
    filterDirText=(String)fields.get("filterDir");
    
    doExportText=(boolean)fields.get("doExportText");
    doExportMarkup=(boolean)fields.get("doExportMarkup");
    doExportTree=(boolean)fields.get("doExportTree");
    
    exportName=(String)fields.get("exportName");
    
    filterExt=getFilters(filterExtText);
    excludeExt=getFilters(excludeExtText);
    filterDir=getFilters(filterDirText);
  }

  /*
   * Scans the directory
   * Outputs the result as text
   * Exports result if checkboxes are selected
   */
  public void processData() {                                   // << Start point >>
    jsonArray = fullScan(path, -1);

    if(textArray.size()==0){
      text="No Data!";
      return;
    }
    
    text = join(textArray, "\n");                                 // internal join() method
    markup = join(markupArray, "\n");

    if(doExportText) exportText();
    if(doExportMarkup) exportMarkup();
    if(doExportTree) exportTree();
  }
  
  /*
   * Recursive scans all subdirectories
   */
  public ArrayList<TreeNode> fullScan(String dir, int level) {
    ArrayList<TreeNode> json, res;
    ArrayList<String> list;
    String[] data;
    String pad;
    File file;
    
    json = new ArrayList<TreeNode>();                               // json is recursive tree structure needed for the jsTree plugin

    file = new File(dir);
    data = file.list();                                             // get string list of files in the current level directory
    list = prepareData(data, dir);                                  // clean of filtered dirs and exts, sort by name and put directories first
    pad = getPadding(level);

    for (String value : list) {
      TreeNode node;
      String item = dir + '/' + value;
      file = new File(item);

      if (file.isDirectory() == true) {                       // directories
        boolean passed=true;
        if(filterDir.size()!=0 && level==-1){                 // filter directories
          passed=filterDirectory(value);                 
        }
        if(!passed) continue;
          
        String currentDir = "[" + value + "]";

        textArray.add(pad + currentDir);                      // add text and markup lines to arrays
        markupArray.add(wrapDir(pad + currentDir));

        res = fullScan(item, level + 1);                      // recursive scan

        node = new DirNode(value, res);
        json.add(node);
      } else {                                                // files
        String currentFile = value;

        textArray.add(pad + currentFile);
        markupArray.add(wrapFile(pad + currentFile));

        node = new FileNode(value, getIcon(value));
        json.add(node);
      }
    }

    return json;
  }

  // --------------------------------------------------- helpers ---------------------------------------------------
  
  /*
   * Filters files and folders
   * Sorts by name and directories-first order
   */
  public ArrayList<String> prepareData(String[] data, String dir) {
    ArrayList<String> folders = new ArrayList<String>(), 
    files = new ArrayList<String>(), list;

    for (String value : data) {
      String item = dir + '/' + value;
      File f = new File(item);

      if (f.isDirectory() == true) {                    // add directories
        folders.add(value);
      } else if (filterFile(value)) {                   // filter files and add
        files.add(value);
      }
    }

    list = getList(folders, files);
    return list;
  }

  /*
   * Merge folders and files arrays
   */
  public ArrayList<String> getList(ArrayList<String> folders, ArrayList<String> files) {
    ArrayList<String> list = new ArrayList<String>();
    Collections.sort(folders);
    Collections.sort(files);
    list.addAll(folders);
    list.addAll(files);
    return list;
  }
  
  /*
   * Formats path, fixes backslashes, trims and removes last slash
   */
  public String formatPath(String path) {
    path=path.replace('\\', '/');
    path=path.trim();
    
    int last=path.length()-1;
    if(path.substring(last)=="/")
      path=path.substring(0,last);
    
    return path;
  }

  /*
   * Replaces strings from the tree template (strings format: '_string_') with the 'replacement' text
   */
  private String replaceTemplate(String tmpl, String replacement, String text){
    text=text.replace(tmpl, replacement);
    return text;
  }
  
  /*
   * Gets the template for the tree view (jsTree plugin)
   */
  private String readTemplate(String tmpl) {
    String doc = "", line = null;
    BufferedReader br = null;

    try {
      br = new BufferedReader(new FileReader("templates/tree.html"));               // read by lines
      while ((line = br.readLine()) != null) {
        doc += line+nl;
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (br != null)
          br.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return doc;
  }

  /*
   * Writes the text to the file
   * filename contains extension
   */
  private void writeFile(String filename, String text) {
    File file;
    PrintWriter writer;
    
    try {
      file = new File(filename);
      file.createNewFile();

      writer = new PrintWriter(filename);
      writer.print(text);
      writer.close();
    } catch (Exception e) {
      System.out.println("error-writing-file: " + e.getMessage());
    }
  }
  
  /*
   * Outputs padding spaces for text output depending on nesting level
   */
  public String getPadding(int level) {
    String resPad = "";
    for (int i = 0; i <= level; i++) {
      resPad += pad;
    }
    return resPad;
  }
  
  /*
   * Returns icon path for the tree view
   */
  private String getIcon(String file){
    String ext, icon, path, iconExt;
    boolean useDefault=true;
    
    ext="";
    icon="jstree-file";
    path=iconsPath;
    iconExt=".png";
    
    Pattern pat=Pattern.compile("\\.[\\w]+$");
    Matcher mat=pat.matcher(file);
    
    if(!mat.find()) return icon;                                // first run find() then get results
    
    ext=mat.group();                                            // string result
    ext=ext.substring(1);
    
    if(useDefault){                                             // process different types of extensions
      for(String item : exts){
        if(item.equals(ext)){
          icon=path+item+iconExt;
          useDefault=false;
          break;
        }
      }
    }
    
    if(useDefault){
      for(String item : imageExts){
        if(item.equals(ext)){
          icon=path+"image"+iconExt;
          useDefault=false;
          break;
        }
      }
    }
    
    if(useDefault){
      for(String item : musicExts){
        if(item.equals(ext)){
          icon=path+"music"+iconExt;
          useDefault=false;
          break;
        }
      }
    }
    
    if(useDefault){
      for(String item : videoExts){
        if(item.equals(ext)){
          icon=path+"video"+iconExt;
          useDefault=false;
          break;
        }
      }
    }
    
    return icon;
  }
  
  /*
   * Joins array items into a string with separators
   */
  public String join(ArrayList<String> array, String separator) {
    String res = "";
    for(int i=0;i<array.size();i++){
      if(i==array.size()-1)
        separator="";
      res += array.get(i) + separator;
    }
    return res;
  }
  
  /*
   * Checks if text matches partially to regex
   */
  public boolean match(String regex, String text) {
    Pattern pat=Pattern.compile(regex);
    return pat.matcher(text).find();
  }
  
// --------------------------------------------------- filters ---------------------------------------------------
  
  /*
   * Cleans, trims and checks filters for emptiness
   */
  public ArrayList<String> getFilters(String filter) {
    ArrayList<String> list=new ArrayList<String>();
    String[] elements;
    filter=filter.trim();
    
    if(filter.length()!=0){
      elements=filter.split("\n");
      Collections.addAll(list, elements);
      
      for(int i=0;i<list.size();i++){
        String item=list.get(i);
        list.set(i,item.trim());
      }
    }
    
    return list;
  }
  
  /*
   * Filters file extensions and returns true if the file will be included in the output
   * If exclude filter is not empty ignores the include filter
   */
  public boolean filterFile(String file) {
    if(excludeExt.size()!=0){
      for(String ext:excludeExt){
        if(match("\\."+ext+"$",file))
          return false;
      }
      return true;
    }
    
    if(filterExt.size()==0) return true;
    for(String ext:filterExt){
      if(match("\\."+ext+"$",file))
        return true;
    }
    return false;
  }
  
  /*
   * Uses form filter to filter directories from the first scanning level
   */
  public boolean filterDirectory(String dir) {
    for(String filter:filterDir){
      if(filter.equals(dir))
        return true;
    }
    return false;
  }
  
  /*
   * Gets text for the tree template
   */
  public String getFiltersText() {
    String filterExtText="", excludeExtText="", filterDirText="", filters="";
    
    if(filterExt.size()!=0){
      filterExtText=join(filterExt, ",");
    }
    if(excludeExt.size()!=0){
      excludeExtText=join(excludeExt, ",");
    }
    if(filterDir.size()!=0){
      filterDirText=join(filterDir, ",");
    }
    
    filters="Files include ["+filterExtText+"]";
    filters+=", Files exclude ["+excludeExtText+"]";
    filters+=", Directories ["+filterDirText+"]";
    
    return filters;
  }
  
// --------------------------------------------------- wrappers ---------------------------------------------------

  public String wrapDir(String dir) {
    return "<span class=\"directory\">" + dir + "</span>";
  }

  public String wrapFile(String file) {
    return "<span class=\"file\">" + file + "</span>";
  }

  public String wrapMarkup(String markup) {
    String res = "<pre>" + nl + markup + "</pre>";
    res = wrapDocument(res);
    return res;
  }

  public String wrapDocument(String markup) {
    return "<meta charset=\"utf-8\">" + nl + markup;
  }

  // --------------------------------------------------- exports ---------------------------------------------------

  /*
   * Exports text to a .txt file in 'export/text'
   */
  private void exportText() {
    File file;
    String exportPath, fileName, ext;

    exportPath = "export/text/";
    ext=".txt";
    fileName = getExportName(ext);
    fileName = exportPath + fileName;
    
    writeFile(fileName,text);
  }
  
  /*
   * Exports HTML markup to a .hmtl file in 'export/markup'
   */
  private void exportMarkup() {
    File file;
    String exportPath, fileName, ext;

    exportPath = "export/markup/";
    ext=".html";
    fileName = getExportName(ext);
    fileName = exportPath + fileName;
    markup = wrapMarkup(markup);

    writeFile(fileName, markup);
  }
  
  /*
   * Exports .json and .html files to the 'export/tree'
   * The .html file can be used directly to view the tree
   * The jsTree plugin must be in the 'tree/lib'
   *
   * The method gets the .html template from 'templates/tree.html', 
   * replaces template strings with the current data and create new .html in the 'exports/tree'
   * Then creates .json in the 'exports/tree/json' which is read by the script in the exported .html page
   */
  private void exportTree() {
    String tmpl, doc, treeName, exportPath, jsonFolder, 
    jsonPath, exportDoc, exportJSON;
    String filters;
    String jsonFile, htmlFile;
    
    Gson gson = new Gson();
    String json = gson.toJson(jsonArray);

    treeName=getExportName(null);                                         // get name
    
    tmpl="templates/tree.html";
    exportPath="export/tree/";
    jsonFolder="json/";
    jsonPath=exportPath+jsonFolder;
    
    exportDoc=treeName+".html";
    exportJSON=treeName+".json";
    
    doc=readTemplate(tmpl);                                               // process template
    doc=replaceTemplate("_jsonPath_", jsonFolder+exportJSON, doc);
    doc=replaceTemplate("_Title_", "Directory: "+treeName, doc);
    doc=replaceTemplate("_FolderPath_", "Directory: "+path, doc);
    
    filters=getFiltersText();
    doc=replaceTemplate("_Filters_", "Filters: "+filters, doc);
    
    htmlFile=exportPath+exportDoc;                                        // get paths
    jsonFile=jsonPath+exportJSON;
      
    writeFile(htmlFile, doc);                                             // write results
    writeFile(jsonFile, json);
  }
  
  /*
   * Returns the name that will be used to export 
   * text, markup and tree views of the directory structure
   */
  private String getExportName(String ext){
    boolean useCurrentDir=true;
    String exportName, name;
    
    exportName="no-name";
    
    if(useCurrentDir){
      Pattern pat=Pattern.compile("/[^/]+$");
      Matcher mat=pat.matcher(path);
      
      if(mat.find()){
        exportName=mat.group();
        exportName=exportName.substring(1);
      }
    }
    
    name=exportName;
    if(ext!=null) name+=ext;
    
    return name;
  }

}
