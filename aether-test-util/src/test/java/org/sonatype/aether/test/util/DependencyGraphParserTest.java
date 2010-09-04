package org.sonatype.aether.test.util;

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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;

/**
 * @author Benjamin Hanzelmann
 */
public class DependencyGraphParserTest
{

    private DependencyGraphParser parser;

    @Before
    public void setup()
    {
        this.parser = new DependencyGraphParser();
    }

    @Test
    public void testOnlyRoot()
        throws IOException
    {
        String def = "gid:aid:1:jar:scope";

        DependencyNode node = parser.parseLiteral( def );

        assertNotNull( node );
        assertEquals( 0, node.getChildren().size() );

        Dependency dependency = node.getDependency();
        assertNotNull( dependency );
        assertEquals( "scope", dependency.getScope() );

        Artifact artifact = dependency.getArtifact();
        assertNotNull( artifact );

        assertEquals( "gid", artifact.getGroupId() );
        assertEquals( "aid", artifact.getArtifactId() );
        assertEquals( "jar", artifact.getExtension() );
        assertEquals( "1", artifact.getVersion() );
    }

    @Test
    public void testOptionalScope()
        throws IOException
    {
        String def = "gid:aid:1:jar";

        DependencyNode node = parser.parseLiteral( def );

        assertNotNull( node );
        assertEquals( 0, node.getChildren().size() );

        Dependency dependency = node.getDependency();
        assertNotNull( dependency );
        assertEquals( "", dependency.getScope() );

    }

    @Test
    public void testWithChildren()
        throws IOException
    {
        String def =
            "gid1:aid1:ver1:ext1:scope1\n" + "+- gid2:aid2:ver2:ext2:scope2\n" + "\\- gid3:aid3:ver3:ext3:scope3\n";

        DependencyNode node = parser.parseLiteral( def );
        assertNotNull( node );

        int idx = 1;

        assertNodeProperties( node, idx++ );

        List<DependencyNode> children = node.getChildren();
        assertEquals( 2, children.size() );

        for ( DependencyNode child : children )
        {
            assertNodeProperties( child, idx++ );
        }

    }

    @Test
    public void testDeepChildren()
        throws IOException
    {
        String def =
            "gid1:aid1:ver1:ext1\n" + "+- gid2:aid2:ver2:ext2:scope2\n" + "|  \\- gid3:aid3:ver3:ext3\n"
                + "\\- gid4:aid4:ver4:ext4:scope4";

        DependencyNode node = parser.parseLiteral( def );
        assertNodeProperties( node, 1 );

        assertEquals( 2, node.getChildren().size() );
        assertNodeProperties( node.getChildren().get( 1 ), 4 );
        DependencyNode lvl1Node = node.getChildren().get( 0 );
        assertNodeProperties( lvl1Node, 2 );

        assertEquals( 1, lvl1Node.getChildren().size() );
        assertNodeProperties( lvl1Node.getChildren().get( 0 ), 3 );
    }

    private void assertNodeProperties( DependencyNode node, int idx )
    {
        assertNodeProperties( node, String.valueOf( idx ) );
    }

    private void assertNodeProperties( DependencyNode node, String suffix )
    {
        assertNotNull( node );
        Dependency dependency = node.getDependency();
        assertNotNull( dependency );
        if ( !"".equals( dependency.getScope() ) )
        {
            assertEquals( "scope" + suffix, dependency.getScope() );
        }

        Artifact artifact = dependency.getArtifact();
        assertNotNull( artifact );

        assertEquals( "gid" + suffix, artifact.getGroupId() );
        assertEquals( "aid" + suffix, artifact.getArtifactId() );
        assertEquals( "ext" + suffix, artifact.getExtension() );
        assertEquals( "ver" + suffix, artifact.getVersion() );
    }

    @Test
    public void testComments()
        throws IOException
    {
        String def = "# first line\n#second line\ngid:aid:ver:ext # root artifact asdf:qwer:zcxv:uip";

        DependencyNode node = parser.parseLiteral( def );

        assertNodeProperties( node, "" );
    }

    @Test
    public void testId()
        throws IOException
    {
        String def = "(id)gid:aid:ver:ext\n\\- ^id";
        DependencyNode node = parser.parseLiteral( def );
        assertNodeProperties( node, "" );

        assertNotNull( node.getChildren() );
        assertEquals( 1, node.getChildren().size() );

        assertSame( node, node.getChildren().get( 0 ) );
    }

    @Test
    public void testResourceLoading()
        throws UnsupportedEncodingException, IOException
    {
        String prefix = "org/sonatype/aether/test/util/";
        String name = "testResourceLoading.txt";

        DependencyNode node = parser.parse( prefix + name );
        assertEquals( 0, node.getChildren().size() );
        assertNodeProperties( node, "" );
    }

    @Test
    public void testResourceLoadingWithPrefix()
        throws UnsupportedEncodingException, IOException
    {
        String prefix = "org/sonatype/aether/test/util/";
        parser = new DependencyGraphParser( prefix );

        String name = "testResourceLoading.txt";

        DependencyNode node = parser.parse( name );
        assertEquals( 0, node.getChildren().size() );
        assertNodeProperties( node, "" );
    }

    @Test
    public void testProperties()
        throws IOException
    {
        String def = "gid:aid:ver:ext;test=foo;test2=fizzle";
        DependencyNode node = parser.parseLiteral( def );

        assertNodeProperties( node, "" );

        Map<String, String> properties = node.getDependency().getArtifact().getProperties();
        assertNotNull( properties );
        assertEquals( 2, properties.size() );

        assertTrue( properties.containsKey( "test" ) );
        assertEquals( "foo", properties.get( "test" ) );
        assertTrue( properties.containsKey( "test2" ) );
        assertEquals( "fizzle", properties.get( "test2" ) );
    }

    @Test
    public void testSubstitutions()
        throws IOException
    {
        parser.setSubstitutions( Arrays.asList( "subst1", "subst2" ) );
        String def = "%s:%s:ver:ext";
        DependencyNode root = parser.parseLiteral( def );
        Artifact artifact = root.getDependency().getArtifact();
        assertEquals( "subst2", artifact.getArtifactId() );
        assertEquals( "subst1", artifact.getGroupId() );
        
        def = "%s:aid:ver:ext\n\\- %s:aid:ver:ext";
        root = parser.parseLiteral( def );

        assertEquals( "subst1", root.getDependency().getArtifact().getGroupId() );
        assertEquals( "subst2", root.getChildren().get( 0 ).getDependency().getArtifact().getGroupId() );
    }

    @Test
    public void testMultiple()
        throws IOException
    {
        String prefix = "org/sonatype/aether/test/util/";
        String name = "testResourceLoading.txt";

        List<DependencyNode> nodes = parser.parseMultiple( prefix + name );

        assertEquals( 2, nodes.size() );
        assertEquals( "aid", nodes.get( 0 ).getDependency().getArtifact().getArtifactId() );
        assertEquals( "aid2", nodes.get( 1 ).getDependency().getArtifact().getArtifactId() );
    }
}
