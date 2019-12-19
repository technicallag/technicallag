import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;

/**
 * Script to create random samples from the list backward dependencies.
 * 
 * @author amjed tahir
 */
public class GenerateRandomSample {

	public static void main(String[] args) throws IOException {

		String src = System.getProperty("user.dir") + "/data/backwards/";
		String dist_sample1 = System.getProperty("user.dir") + "/data/sample1/";
		String dist_sample2 = System.getProperty("user.dir") + "/data/sample2/";

		List<File> listOfFiles = listFiles(src);

		// set sample size here based on the population
		// use https://www.surveysystem.com/sscalc.htm 
		int sampleSize = 368;
		int fileSize = 0;

		Random rand = new Random();
		List<File> sample = new ArrayList<>();

		// Collections.shuffle(randomFiles);
		for (File file : listOfFiles) {
			if (file.getName().endsWith(".csv")) {
				sample.add(file);
				fileSize++;
			}
		}


		// generate a random sample based on a set sample size
		List<File> randomSample = new ArrayList<>();


		File tempFile;

		for (int i = 0; i < sampleSize; i++) {
			tempFile = sample.get(rand.nextInt(fileSize));
			while (randomSample.contains(tempFile)) {
				tempFile = sample.get(rand.nextInt(fileSize));
			}
			randomSample.add(tempFile);
		}

		makeDirectory(dist_sample1);
		makeDirectory(dist_sample2);

		

//		System.out.println("randomSample = " + randomSample.size());

		
		List[] sampleData = split(randomSample);
		
//		System.out.println(sampleData[0].size());
//		System.out.println(sampleData[1].size());

		sampleOutput(dist_sample1, sampleData[0]);
		sampleOutput(dist_sample2, sampleData[1]);
}

	protected static void sampleOutput(String dist_sample1, List sampleData) throws IOException {
		for (int i=0; i<sampleData.size(); i++) {
			File file = (File) sampleData.get(i);
			Files.copy(file.toPath(), (new File(dist_sample1 + file.getName())).toPath(), 
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	
	// make a new directory
	private static boolean makeDirectory(String path) {
		if (Files.exists(Paths.get(path))) {
			try {
				FileUtils.deleteDirectory(new File(path));
			} catch (IOException ex) {
				System.err.println("Failed to create the directory!");
				return false;
			}
		}
		if (new File(path).mkdirs()) {
			return true;
		}
		return false;
	}

	// get all the files from a directory
	public static List<File> listFiles(String directoryName) {
		File directory = new File(directoryName);

		List<File> resultList = new ArrayList<File>();

		File[] fList = directory.listFiles();
		resultList.addAll(Arrays.asList(fList));
		for (File file : fList) {
			if (file.isFile() && file.getName().endsWith(".csv")) {
//				System.out.println(file.getName());
			} else if (file.isDirectory() && file.getName().endsWith(".csv")) {
				resultList.addAll(listFiles(file.getAbsolutePath()));
			}
		}
		return resultList;
	}

	//split the sample size  into two sets
	public static <T> List[] split(List<T> list) {
		List<List<T>> lists = ListUtils.partition(list, (list.size() + 1) / 2);

		return new List[] { lists.get(0), lists.get(1) };
	}

}
