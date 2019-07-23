package io.kurumi.ntt.fragment.rss;

import cn.hutool.log.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class HTMLFilter {

	private static final int REGEX_FLAGS_SI = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
	private static final Pattern P_TAGS = Pattern.compile("<(.*?)>",Pattern.DOTALL);
	private static final Pattern P_END_TAG = Pattern.compile("^/([a-z0-9]+)",REGEX_FLAGS_SI);
	private static final Pattern P_START_TAG = Pattern.compile("^([a-z0-9]+)(.*?)(/?)$",REGEX_FLAGS_SI);
	private static final Pattern P_QUOTED_ATTRIBUTES = Pattern.compile("([a-z0-9]+)=([\"'])(.*?)\\2",REGEX_FLAGS_SI);
	private static final Pattern P_UNQUOTED_ATTRIBUTES = Pattern.compile("([a-z0-9]+)(=)([^\"\\s']+)",REGEX_FLAGS_SI);
	private static final Pattern P_END_ARROW = Pattern.compile("^>");
	private static final Pattern P_BODY_TO_END = Pattern.compile("<([^>]*?)(?=<|$)");
	private static final Pattern P_XML_CONTENT = Pattern.compile("(^|>)([^<]*?)(?=>)");

	// @xxx could grow large... maybe use sesat's ReferenceMap
	private static final ConcurrentMap<String, Pattern> P_REMOVE_PAIR_BLANKS = new ConcurrentHashMap<String, Pattern>();
	private static final ConcurrentMap<String, Pattern> P_REMOVE_SELF_BLANKS = new ConcurrentHashMap<String, Pattern>();

	/** set of allowed html elements, along with allowed attributes for each element **/
	private final Map<String, List<String>> vAllowed;
	/** counts of open tags for each (allowable) html element **/
	private final Map<String, Integer> vTagCounts = new HashMap<String, Integer>();

	/** html elements which must always be self-closing (e.g. "<img />") **/
	private final String[] vSelfClosingTags;
	/** html elements which must always have separate opening and closing tags (e.g. "<b></b>") **/
	private final String[] vNeedClosingTags;
	private final String[] vRemoveBlanks;

	private final boolean encodeQuotes;

	public HTMLFilter(boolean noImg) {

		vAllowed = new HashMap<String, List<String>>();

		final ArrayList<String> a_atts = new ArrayList<String>();

		a_atts.add("href");

		vAllowed.put("a",a_atts);

		if (!noImg) {

			final ArrayList<String> img_atts = new ArrayList<String>();

			img_atts.add("src");

			vAllowed.put("img",img_atts);

		}

		final ArrayList<String> no_atts = new ArrayList<String>();

		vAllowed.put("b",no_atts);
		vAllowed.put("i",no_atts);
		vAllowed.put("pre",no_atts);
		vAllowed.put("code",no_atts);
		vAllowed.put("em",no_atts);

		vSelfClosingTags = new String[] { "img" };
		vNeedClosingTags = new String[] { "a", "b", "code","pre", "i", "em" };
		vRemoveBlanks = new String[] { "a", "b", "pre","code", "i", "em" };
		encodeQuotes = true;
	}

	public String filter(final String input) {

		vTagCounts.clear();

		String s = input;

		s = balanceHTML(s);
		s = checkTags(s);
		s = processRemoveBlanks(s);

		return s;
	}

	private String balanceHTML(String s) {

		s = regexReplace(P_END_ARROW,"",s);
		s = regexReplace(P_BODY_TO_END,"<$1>",s);
		s = regexReplace(P_XML_CONTENT,"$1<$2",s);

		return s;
	}

	private String checkTags(String s) {

		Matcher m = P_TAGS.matcher(s);

		final StringBuffer buf = new StringBuffer();
		while (m.find()) {
			String replaceStr = m.group(1);
			replaceStr = processTag(replaceStr);
			m.appendReplacement(buf,Matcher.quoteReplacement(replaceStr));
		}
		m.appendTail(buf);

		s = buf.toString();

		// these get tallied in processTag
		// (remember to reset before subsequent calls to filter method)
		for (String key : vTagCounts.keySet()) {
			for (int ii = 0; ii < vTagCounts.get(key); ii++) {
				s += "</" + key + ">";
			}
		}

		return s;
	}

	private String processRemoveBlanks(final String s) {
		String result = s;
		for (String tag : vRemoveBlanks) {
			if (!P_REMOVE_PAIR_BLANKS.containsKey(tag)) {
				P_REMOVE_PAIR_BLANKS.putIfAbsent(tag,Pattern.compile("<" + tag + "(\\s[^>]*)?></" + tag + ">"));
			}
			result = regexReplace(P_REMOVE_PAIR_BLANKS.get(tag),"",result);
			if (!P_REMOVE_SELF_BLANKS.containsKey(tag)) {
				P_REMOVE_SELF_BLANKS.putIfAbsent(tag,Pattern.compile("<" + tag + "(\\s[^>]*)?/>"));
			}
			result = regexReplace(P_REMOVE_SELF_BLANKS.get(tag),"",result);
		}

		return result;
	}

	private static String regexReplace(final Pattern regex_pattern,final String replacement,final String s) {
		Matcher m = regex_pattern.matcher(s);
		return m.replaceAll(replacement);
	}

	private String processTag(final String s) {
		// ending tags
		Matcher m = P_END_TAG.matcher(s);
		if (m.find()) {
			final String name = m.group(1).toLowerCase();
			if (allowed(name)) {
				if (false == inArray(name,vSelfClosingTags)) {
					if (vTagCounts.containsKey(name)) {
						vTagCounts.put(name,vTagCounts.get(name) - 1);
						return "</" + name + ">";
					}
				}
			}
		}

		// starting tags
		m = P_START_TAG.matcher(s);
		if (m.find()) {
			final String name = m.group(1).toLowerCase();
			final String body = m.group(2);
			String ending = m.group(3);

			// debug( "in a starting tag, name='" + name + "'; body='" + body + "'; ending='" + ending + "'" );
			if (allowed(name)) {
				String params = "";

				final Matcher m2 = P_QUOTED_ATTRIBUTES.matcher(body);
				final Matcher m3 = P_UNQUOTED_ATTRIBUTES.matcher(body);
				final List<String> paramNames = new ArrayList<String>();
				final List<String> paramValues = new ArrayList<String>();
				while (m2.find()) {
					paramNames.add(m2.group(1)); // ([a-z0-9]+)
					paramValues.add(m2.group(3)); // (.*?)
				}
				while (m3.find()) {
					paramNames.add(m3.group(1)); // ([a-z0-9]+)
					paramValues.add(m3.group(3)); // ([^\"\\s']+)
				}

				String paramName, paramValue;
				for (int ii = 0; ii < paramNames.size(); ii++) {
					paramName = paramNames.get(ii).toLowerCase();
					paramValue = paramValues.get(ii);

					// debug( "paramName='" + paramName + "'" );
					// debug( "paramValue='" + paramValue + "'" );
					// debug( "allowed? " + vAllowed.get( name ).contains( paramName ) );

					if (allowedAttribute(name,paramName)) {
						params += " " + paramName + "=\"" + paramValue + "\"";
					}
				}

				if (inArray(name,vSelfClosingTags)) {
					ending = " /";
				}

				if (inArray(name,vNeedClosingTags)) {
					ending = "";
				}

				if (ending == null || ending.length() < 1) {
					if (vTagCounts.containsKey(name)) {
						vTagCounts.put(name,vTagCounts.get(name) + 1);
					} else {
						vTagCounts.put(name,1);
					}
				} else {
					ending = " /";
				}
				return "<" + name + params + ending + ">";
			} else {
				return "";
			}
		}

		return "";
	}

	private static boolean inArray(final String s,final String[] array) {
		for (String item : array) {
			if (item != null && item.equals(s)) {
				return true;
			}
		}
		return false;
	}

	private boolean allowed(final String name) {
		return (vAllowed.isEmpty() || vAllowed.containsKey(name));
	}

	private boolean allowedAttribute(final String name,final String paramName) {
		return allowed(name) && (vAllowed.isEmpty() || vAllowed.get(name).contains(paramName));
	}
}

