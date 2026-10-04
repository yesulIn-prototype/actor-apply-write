package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FormDeletionStateTest {
    @TempDir Path directory;

    @Test
    void restore_PreservesClosedPublishedState_AndReadsLegacyState() throws Exception {
        // given: previous installations did not write deleted
        FormStore store = new FormStore(directory.toString(), JsonMapper.builder().build());
        Vid vid = new Vid("80001");
        store.draft(vid);
        Files.writeString(directory.resolve("80001/state.json"), "{\"published\":[1],\"closed\":true}");
        assertThat(store.state(vid).deleted()).isFalse();
        // when
        store.markDeleted(vid, true);
        FormStore reloaded = new FormStore(directory.toString(), JsonMapper.builder().build());
        assertThat(reloaded.state(vid).deleted()).isTrue();
        reloaded.markDeleted(vid, false);
        // then
        assertThat(reloaded.state(vid)).isEqualTo(new FormStore.State(java.util.List.of(1), true, false));
    }

    @Test
    void restore_KeepsDraftUnpublished_WhenDraftWasDeleted() throws Exception {
        // given
        FormStore store = new FormStore(directory.toString(), JsonMapper.builder().build());
        Vid vid = new Vid("80002");
        store.draft(vid);
        // when
        store.markDeleted(vid, true);
        store.markDeleted(vid, false);
        // then
        assertThat(store.state(vid).current()).isEmpty();
        assertThat(store.state(vid).closed()).isFalse();
    }
}
