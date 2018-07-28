package timaxa007.config_tlx;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HandlerConfig {

	static enum Pattern {
		SECTION,
		INHERITANCE,
		KEY,
		VALUE,
		INCLUDE,
		COMMENT_LINE,
		COMMENT_MASSIVE;
	}

	//HashMap (-) ->|<- TreeMap (A-Z) ->|<- LinkedHashMap (List)
	public static void load(final File file, final Map<String, SectionContainer> map) {
		FileReader fr = null;

		try{
			fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			try {
				System.out.println("File - " + file.getAbsolutePath());
				int c = -1, lc = -1;

				Pattern p = Pattern.KEY;
				Pattern lp = p;

				StringBuilder sb = new StringBuilder();
				String
				sectionName = "",
				keyName = "",
				valueName = "";

				final ArrayList<String> includes = new ArrayList<String>();

				while((c = br.read()) != -1) {
					/////////////////////////////////////////////////
					if (c == ' ' && p != Pattern.VALUE) continue;
					else if (c == '\t') continue;
					/////////////////////////////////////////////////
					else if (c == '\r' || c == '\n') {
						if (p == Pattern.COMMENT_MASSIVE) continue;
						else if (p == Pattern.INHERITANCE) {
							if (!map.containsKey(sectionName)) map.put(sectionName, new SectionContainer());
							map.get(sectionName).inheritance.add(sb.toString());
						} else if (p == Pattern.INCLUDE) {
							includes.add(sb.toString());
							for (String s : includes) load(new File(s), map);
							includes.clear();
						} else
							valueName = sb.toString().trim();
						sb.setLength(0);
						sb.trimToSize();
						if (keyName.length() > 0) {
							if (!map.containsKey(sectionName)) map.put(sectionName, new SectionContainer());
							map.get(sectionName).main.put(keyName, valueName);
						}
						keyName = valueName = "";
						lp = p;
						p = Pattern.KEY;
						lc = -1;
						continue;
					}
					/////////////////////////////////////////////////
					else if (lc == '/' && c == '*') {
						lp = p;
						p = Pattern.COMMENT_MASSIVE;
						if (sb.length() > 0)
							sb.deleteCharAt(sb.length() - 1);
					}

					else if (lc == '*' && c == '/') {
						p = lp;
						lp = Pattern.COMMENT_MASSIVE;
					}

					else if (p == Pattern.COMMENT_LINE || p == Pattern.COMMENT_MASSIVE) {
						lc = c;
						continue;
					}

					else if (lc == '/' && c == '/') {
						p = Pattern.COMMENT_LINE;
						if (sb.length() > 0)
							sb.deleteCharAt(sb.length() - 1);
					}
					/////////////////////////////////////////////////
					else if (c == '=' && p == Pattern.KEY) {
						keyName = sb.toString();
						sb.setLength(0);
						sb.trimToSize();
						p = Pattern.VALUE;
					}
					/////////////////////////////////////////////////
					else if (sb.toString().equals("@include")) {
						sb.setLength(0);
						sb.trimToSize();
						sb.append((char)c);
						lp = p;
						p = Pattern.INCLUDE;
					}

					else if (c == ':' && p == Pattern.INCLUDE) {
						includes.add(sb.toString());
						sb.setLength(0);
						sb.trimToSize();
					}
					/////////////////////////////////////////////////
					else if (c == '[') {
						if (sectionName.length() > 0) {
							if (!map.containsKey(sectionName)) map.put(sectionName, new SectionContainer());
							if (keyName.length() > 0) {
								map.get(sectionName).main.put(keyName, valueName);
								keyName = valueName = "";
							}
							sectionName = "";
						}
						p = Pattern.SECTION;
					}

					else if (c == ']') {
						sectionName = sb.toString();
						sb.setLength(0);
						sb.trimToSize();
						p = Pattern.KEY;
					}

					else if (lc == ']' && c == ':') {
						p = Pattern.INHERITANCE;
					}

					else if (c == ':' && p == Pattern.INHERITANCE) {
						if (!map.containsKey(sectionName)) map.put(sectionName, new SectionContainer());
						map.get(sectionName).inheritance.add(sb.toString());
						sb.setLength(0);
						sb.trimToSize();
					}
					/////////////////////////////////////////////////
					else {
						if (p != Pattern.COMMENT_LINE || p != Pattern.COMMENT_MASSIVE) sb.append((char)c);
					}
					/////////////////////////////////////////////////
					lc = c;
				}
				if (sectionName.length() > 0) {
					if (!map.containsKey(sectionName)) map.put(sectionName, new SectionContainer());
					if (keyName.length() > 0) {
						map.get(sectionName).main.put(keyName, valueName);
						keyName = valueName = "";
					}
				}
			}catch(IOException e){e.printStackTrace();}
			fr.close();
		}catch(Exception e){e.printStackTrace();}

	}

	public static void process(final File file, final Map<String, SectionContainer> map, final IProcessConfig processConfig) {
		load(file, map);
		/////////////////////////////////////////////////////////////////////////////////////
		//if (Option.debug) mess(map);
		/////////////////////////////////////////////////////////////////////////////////////
		for (Map.Entry<String, SectionContainer> entry : map.entrySet()) {
			//if (entry.getValue().inheritance.contains("exclude")) continue;
			filled(entry.getValue().inheritance, entry.getValue().other, map);
		}
		/////////////////////////////////////////////////////////////////////////////////////
		//if (Option.debug) mess(map);
		/////////////////////////////////////////////////////////////////////////////////////
		for (Map.Entry<String, SectionContainer> entry : map.entrySet()) {
			//if (entry.getValue().inheritance.contains("exclude")) continue;

			for (Map.Entry<String, String> entry2 : entry.getValue().main.entrySet())
				if (entry.getValue().other.containsKey(entry2.getKey()))
					entry.getValue().other.replace(entry2.getKey(), entry2.getValue());
				else
					entry.getValue().other.put(entry2.getKey(), entry2.getValue());
		}

		for (Map.Entry<String, SectionContainer> entry : map.entrySet()) {
			if (entry.getValue().inheritance.contains("exclude")) continue;
			Object item = processConfig.newInstance(entry.getKey());
			for (Map.Entry<String, String> entry2 : entry.getValue().other.entrySet())
				processConfig.process(entry2.getKey(), entry2.getValue(), item);
			processConfig.finish(item, entry.getKey());
		}
		/////////////////////////////////////////////////////////////////////////////////////
		//mess(map);
		/////////////////////////////////////////////////////////////////////////////////////
	}

	private static void filled(final ArrayList<String> inheritance, final HashMap<String, String> other, final Map<String, SectionContainer> map) {
		if (inheritance.isEmpty()) return;
		//if (inheritance.contains("exclude")) return;

		for (int i = 0; i < inheritance.size(); ++i) {
			String is = inheritance.get(i);
			if (is.equals("exclude")) continue;
			if (map.containsKey(is)) {
				SectionContainer sc = map.get(is);

				if (!sc.inheritance.isEmpty()) filled(sc.inheritance, sc.other, map);

				if (sc.main.isEmpty()) continue;

				for (Map.Entry<String, String> entry2 : sc.other.entrySet()) {
					if (other.containsKey(entry2.getKey()))
						other.replace(entry2.getKey(), entry2.getValue());
					else
						other.put(entry2.getKey(), entry2.getValue());
				}

				for (Map.Entry<String, String> entry2 : sc.main.entrySet()) {
					if (other.containsKey(entry2.getKey()))
						other.replace(entry2.getKey(), entry2.getValue());
					else
						other.put(entry2.getKey(), entry2.getValue());
				}

			}
			//inheritance.remove(i);
		}
		//inheritance.clear();
	}

	private static void mess(final Map<String, SectionContainer> map) {
		System.out.println();
		System.out.println("----------------------------------------------");
		for (Map.Entry<String, SectionContainer> entry : map.entrySet()) {
			System.out.println();
			System.out.print("[");
			System.out.print(entry.getKey());
			System.out.print("]");
			if (!entry.getValue().inheritance.isEmpty()) {
				System.out.print(" |");
				for (String is : entry.getValue().inheritance) {
					System.out.print(" ");
					System.out.print(is);
					System.out.print(" |");
				}
			}
			System.out.println();
			for (Map.Entry<String, String> entry2 : entry.getValue().main.entrySet())
				System.out.println("M-| " + entry2.getKey() + " = " + entry2.getValue() + " |");
			for (Map.Entry<String, String> entry2 : entry.getValue().other.entrySet())
				System.out.println("O-| " + entry2.getKey() + " = " + entry2.getValue() + " |");
		}
		System.out.println("----------------------------------------------");
		System.out.println();
	}

}
