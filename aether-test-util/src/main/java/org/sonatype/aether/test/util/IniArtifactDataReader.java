/*
 * Copyright (c) 2010 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, 
 * and you may not use this file except in compliance with the Apache License Version 2.0. 
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the Apache License Version 2.0 is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.aether.test.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.test.util.impl.StubArtifact;

/**
 * A parser for an artifact description in an INI-like format.
 * <p>
 * Possible sections are:
 * <ul>
 * <li>relocations</li>
 * <li>dependencies</li>
 * <li>managedDependencies</li>
 * <li>repositories</li>
 * </ul>
 * The relocation- and dependency-sections contain artifact coordinates of the form:
 * 
 * <pre>
 * gid:aid:ver:ext[:scope][:optional]
 * </pre>
 * 
 * The dependency-sections may specify exclusions:
 * 
 * <pre>
 * -gid:aid
 * </pre>
 * 
 * A repository definition is of the form:
 * 
 * <pre>
 * id:type:url
 * </pre>
 * 
 * <h2>Example</h2>
 * 
 * <pre>
 * [relocation]
 * gid:aid:ver:ext
 * 
 * [dependencies]
 * gid:aid:ver:ext:scope
 * -exclusion:aid
 * gid:aid2:ver:ext:scope:optional
 * 
 * [managed-dependencies]
 * gid:aid2:ver2:ext:scope
 * -gid:aid
 * -gid:aid
 * 
 * [repositories]
 * id:type:file:///test-repo
 * </pre>
 * 
 * @see IniArtifactDescriptorReader
 * @author Benjamin Hanzelmann
 */
public class IniArtifactDataReader
{

    private String prefix = "";

    /**
     * Constructs a data reader with the prefix <code>""</code>.
     */
    public IniArtifactDataReader()
    {
        this( "" );
    }

    /**
     * Constructs a data reader with the given prefix.
     * 
     * @param prefix the prefix to use for loading resources from the classpath.
     */
    public IniArtifactDataReader( String prefix )
    {
        this.prefix = prefix;

    }

    /**
     * Load an artifact description from the classpath and parse it.
     */
    public ArtifactDescription parse( String resource )
        throws IOException
    {
        URL res = this.getClass().getClassLoader().getResource( prefix + resource );

        if ( res == null )
        {
            throw new IllegalArgumentException( "cannot find resource: " + resource );
        }
        return parse( res );
    }

    /**
     * Open the given URL and parse ist.
     */
    public ArtifactDescription parse( URL res )
        throws IOException
    {
        return parse( new InputStreamReader( res.openStream(), "UTF-8" ) );
    }

    /**
     * Parse the given String.
     */
    public ArtifactDescription parseLiteral( String description )
        throws IOException
    {
        StringReader reader = new StringReader( description );
        return parse( reader );
    }

    private enum State
    {
        NONE, RELOCATIONS, DEPENDENCIES, MANAGEDDEPENDENCIES, REPOSITORIES
    }

    private ArtifactDescription parse( Reader reader )
        throws IOException
    {

        String line = null;

        BufferedReader in = new BufferedReader( reader );

        State state = State.NONE;

        Map<State, List<String>> sections = new HashMap<State, List<String>>();

        while ( ( line = in.readLine() ) != null )
        {

            line = cutComment( line );
            if ( isEmpty( line ) )
            {
                continue;
            }

            if ( line.startsWith( "[" ) )
            {
                state = State.valueOf( line.substring( 1, line.length() - 1 ).toUpperCase( Locale.ENGLISH ) );
                sections.put( state, new ArrayList<String>() );
            }
            else
            {
                sections.get( state ).add( line.trim() );
            }
        }

        List<Artifact> relocations = relocations( sections.get( State.RELOCATIONS ) );
        List<Dependency> dependencies = dependencies( sections.get( State.DEPENDENCIES ) );
        List<Dependency> managedDependencies = dependencies( sections.get( State.MANAGEDDEPENDENCIES ) );
        List<RemoteRepository> repositories = repositories( sections.get( State.REPOSITORIES ) );

        ArtifactDescription description =
            new ArtifactDescription( relocations, dependencies, managedDependencies, repositories );
        return description;
    }

    private List<RemoteRepository> repositories( List<String> list )
    {
        ArrayList<RemoteRepository> ret = new ArrayList<RemoteRepository>();
        if ( list == null )
        {
            return ret;
        }
        for ( String coords : list )
        {
            String[] split = coords.split( ":", 3 );
            String id = split[0];
            String type = split[1];
            String url = split[2];

            ret.add( new RemoteRepository( id, type, url ) );
        }
        return ret;
    }

    /**
     * @param list
     * @return
     */
    private List<Dependency> dependencies( List<String> list )
    {
        List<Dependency> ret = new ArrayList<Dependency>();
        if ( list == null )
        {
            return ret;
        }

        Collection<Exclusion> exclusions = new ArrayList<Exclusion>();

        boolean optional = false;
        Artifact artifact = null;
        String scope = null;

        for ( String coords : list )
        {
            if ( coords.startsWith( "-" ) )
            {
                coords = coords.substring( 1 );
                String[] split = coords.split( ":" );
                exclusions.add( new Exclusion( split[0], split[1], "", "" ) );
            }
            else
            {
                if ( artifact != null )
                {
                    // commit dependency
                    Dependency dep = new Dependency( artifact, scope, optional, exclusions );
                    ret.add( dep );

                    exclusions = new ArrayList<Exclusion>();
                }

                ArtifactDefinition def = new ArtifactDefinition( coords );

                optional = def.isOptional();

                scope = "".equals( def.getScope() ) ? "compile" : def.getScope();

                artifact =
                    new StubArtifact( def.getGroupId(), def.getArtifactId(), "", def.getExtension(), def.getVersion() );
            }
        }
            if ( artifact != null )
            {
                // commit dependency
                Dependency dep = new Dependency( artifact, scope, optional, exclusions );
                ret.add( dep );

                exclusions = new ArrayList<Exclusion>();
            }

        return ret;
    }

    private List<Artifact> relocations( List<String> list )
    {
        List<Artifact> ret = new ArrayList<Artifact>();
        if ( list == null )
        {
            return ret;
        }
        for ( String coords : list )
        {
            ret.add( new StubArtifact( coords ) );
        }
        return ret;
    }

    private static boolean isEmpty( String line )
    {
        return line == null || line.length() == 0;
    }

    private static String cutComment( String line )
    {
        int idx = line.indexOf( '#' );

        if ( idx != -1 )
        {
            line = line.substring( 0, idx );
        }

        return line;
    }

}
