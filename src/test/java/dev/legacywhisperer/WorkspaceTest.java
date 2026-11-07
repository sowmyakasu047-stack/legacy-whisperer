package dev.legacywhisperer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legacywhisperer.web.Workspace;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    @Test
    void acceptsFoldersInsideTheWorkspace() throws Exception {
        Workspace ws = new Workspace(".");
        assertThat(ws.resolve("sample-legacy")).isDirectory();
    }

    @Test
    void rejectsParentAndAbsolutePaths() throws Exception {
        Workspace ws = new Workspace("sample-legacy");
        assertThatThrownBy(() -> ws.resolve("..")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> ws.resolve("/etc")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> ws.resolve("../src")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingBlankAndFilePaths() throws Exception {
        Workspace ws = new Workspace(".");
        assertThatThrownBy(() -> ws.resolve("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ws.resolve("no-such-folder")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ws.resolve("pom.xml")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a folder");
    }
}
