import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class HtmlImageLister {
    public static List<File> getFilesRecursive(String path) {
        List<File> fileList = new ArrayList<File>();
        File[] fileArray = new File(path).listFiles();
        
        for(File f : fileArray) {
            if(f.isDirectory()) { 
                fileList.addAll(getFilesRecursive(f.getPath()));
            } else {
                fileList.add(f);
            }
        }
        
        return fileList;
    }
    
    // Searches for images in html files and outputs the paths to these files in stdout
    public static void main(String[] args) {
        try {
            if(args.length == 0) {
                System.err.println("Please provide a path!\n Usage: java -jar HtmlImageLister.jar <path>");
                System.exit(-1);
            }
            
            List<File> fileList = HtmlImageLister.getFilesRecursive(args[0]);
            Set<String> imageSet = new HashSet<>();
            
            for(File f : fileList) {
                Document doc = Jsoup.parse(f, "UTF-8");
                Elements els = doc.getElementsByTag("img");
                
                for(Element el : els) {
                    imageSet.add(el.attr("src").replaceFirst("../", "")); // path to image has to be shortened
                }
            }
            
            for(String s : imageSet) {
                System.out.println(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}