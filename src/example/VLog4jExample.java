package example;

/*-
 * #%L
 * VLog4j Example
 * %%
 * Copyright (C) 2019 VLog4j Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.semanticweb.vlog4j.core.model.api.PositiveLiteral;
import org.semanticweb.vlog4j.core.reasoner.KnowledgeBase;
import org.semanticweb.vlog4j.core.reasoner.QueryResultIterator;
import org.semanticweb.vlog4j.core.reasoner.Reasoner;
import org.semanticweb.vlog4j.core.reasoner.implementation.VLogReasoner;
import org.semanticweb.vlog4j.parser.ParsingException;
import org.semanticweb.vlog4j.parser.RuleParser;

/**
 * This little example illustrates the use of VLog4j with several rules and data
 * sources. It can be modified to create own VLog4j applications.
 *
 * For the sake of the example, we split inputs across several sources, although
 * one could get the same data more easily (all data used here is derived from
 * Wikidata).
 *
 * @author Markus Kroetzsch
 *
 */
public class VLog4jExample {

	static String sparqlServiceUrl = "https://query.wikidata.org/sparql";

	public static void main(final String[] args) throws ParsingException, IOException {
		configureLogging(Level.INFO);

		final KnowledgeBase kb = RuleParser.parse(getArtistGenreRules());
		RuleParser.parseInto(kb, getHomepageRules());

		try (final Reasoner reasoner = new VLogReasoner(kb)) {
			// reasoner.setLogLevel(LogLevel.INFO); // < uncomment for reasoning details
			reasoner.reason();

			PositiveLiteral query;

			// Run three queries and print the results:
			query = RuleParser.parsePositiveLiteral("genre(?Artist, RockMusic)");
			System.out.println("\nArtists inferred to play some kind of rock music:");
			try (final QueryResultIterator answers = reasoner.answerQuery(query, true)) {
				answers.forEachRemaining(answer -> System.out.println(" " + answer.getTerms().get(0)));
			}

			query = RuleParser.parsePositiveLiteral("artistHomepage(?Artist,?Url)");
			System.out.println("\nArtists for which we found homepages:");
			try (final QueryResultIterator answers = reasoner.answerQuery(query, true)) {
				answers.forEachRemaining(answer -> System.out.println(" " + answer.getTerms()));
			}

			query = RuleParser.parsePositiveLiteral("artistWithoutHomepage(?Artist)");
			System.out.println("\nArtists for which we found no homepages:");
			try (final QueryResultIterator answers = reasoner.answerQuery(query, true)) {
				answers.forEachRemaining(answer -> System.out.println(" " + answer.getTerms().get(0)));
			}

		}
	}

	/**
	 * Defines how messages should be logged. This method can be modified to
	 * restrict the logging messages that are shown on the console or to change
	 * their formatting. See the documentation of Log4J for details on how to do
	 * this.
	 *
	 * Note: The VLog C++ backend performs its own logging that is configured
	 * independently with the reasoner. It is also possible to specify a separate
	 * log file for this part of the logs.
	 *
	 * @param level the log level to be used
	 */
	static void configureLogging(Level level) {
		// Create the appender that will write log messages to the console.
		final ConsoleAppender consoleAppender = new ConsoleAppender();
		// Define the pattern of log messages.
		// Insert the string "%c{1}:%L" to also show class name and line.
		final String pattern = "%d{yyyy-MM-dd HH:mm:ss} %-5p - %m%n";
		consoleAppender.setLayout(new PatternLayout(pattern));
		// Change to Level.ERROR for fewer messages:
		consoleAppender.setThreshold(level);

		consoleAppender.activateOptions();
		Logger.getRootLogger().addAppender(consoleAppender);
	}

	/**
	 * Returns the string representation of a knowledge base that loads musical
	 * sub-genre relations from a CSV file, and combines them with some example
	 * facts about musical artists to infer all of their genres (including indirec
	 * ones higher up in the hierarchy).
	 *
	 * @return string of a knowledge base
	 */
	static String getArtistGenreRules() {
		return "" // "Load hierarchy of musical genres from file:"
				+ "@source subgenre[2] : load-csv('resources/music-subgenres.csv') . \n" //
				// Add some further facts about musical artists:
				+ "genre(Radiohead,AlternativeRock) .  genre(Radiohead,NeoProgressiveRock) . \n" //
				+ "genre(Boygenius,IndieRock) . \n" //
				+ "genre(BandaInternationale,WorldMusic) . \n" //
				+ "genre(LesleyKernochan,Country) . \n" //
				+ "genre(DieArt,PostpPunk) .  genre(DieArt,NewWave) . \n\n" //
				// And a rule that infers additional (super) genres of the ones given:
				+ "genre(?Artist,?Genre2) :- genre(?Artist,?Genre), subgenre(?Genre,?Genre2) . \n\n";
	}

	/**
	 * Returns the string representation of a knowledge base that uses a (gzipped)
	 * RDF file to obtain mappings from Wikidata URIs to MusicBrainz identifiers.
	 * This mapping file is used to connect facts that store the MusicBrainz id of
	 * several artists with a SPARQL query that fetches the homepage for a given
	 * Wikidata entity. As a result, we obtain homepages for our artists. Further
	 * rules are used to also compute those artists for which no homepage was found
	 * (either Wikidata had no homepage, or themapping file did not contain a
	 * Wikidata URI for the artist in the first place).
	 *
	 * @return string of a knowledge base
	 */
	static String getHomepageRules() {
		// Wikidata SPARQL pattern: find the official homepage of any Wikidata entity
		final String sparql = "?entity wdt:P856 ?homepage . "; // official website: ?homepage

		return "" // Configure external data sources:
				+ "@source homepage[2] : sparql(<" + sparqlServiceUrl + ">, 'entity,homepage', '" + sparql + "') .\n" //
				+ "@source wdToMb[3] : load-rdf('resources/wikidataMusicBrainz.nt') . \n\n" //
				// The following facts connect artists with their MusicBrainz identifier:
				+ "musicBrainzId(Boygenius, '3ceeddbd-fba5-4bdb-99f7-2d028ed5afda') . \n" //
				+ "musicBrainzId(BandaInternationale, 'b2b6166b-4746-4734-836d-3ac43f77938b') . \n" //
				+ "musicBrainzId(LesleyKernochan, 'ffa05509-8a1b-4479-ab34-bf8ae8cc23fb') . \n" //
				+ "musicBrainzId(DieArt, 'e6556326-6198-4c92-8f4f-9d4e0fe54711') . \n" //
				+ "musicBrainzId(Radiohead, 'a74b1b7f-71a5-4011-9441-d0b5e4122711') . \n\n" //
				// Relate artists to Wikidata via MusicBrainz-Wikidata mapping (from RDF):
				+ "artistWikidata(?Artist,?WikidataId) :- musicBrainzId(?Artist,?Id), wdToMb(?WikidataId, <http://example.org/musicBrainzId>, ?Id) . \n" //
				// Relate artists to their homapge via Wikidata homepage SPARQ query:
				+ "artistHomepage(?Band,?Url) :- artistWikidata(?Band,?WikidataId), homepage(?WikidataId, ?Url) . \n\n" //
				// Apply projection and negation to define artists without known homepage:
				+ "artistWithHomepage(?Band) :- artistHomepage(?Band,?Url) . \n" //
				+ "artistWithoutHomepage(?Band) :- musicBrainzId(?Band,?Id), ~artistWithHomepage(?Band) . ";//

	}

}
