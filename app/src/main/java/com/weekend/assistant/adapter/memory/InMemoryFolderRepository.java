package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.port.FolderRepository;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryFolderRepository extends InMemoryEntityRepository<Folder> implements FolderRepository {
    public InMemoryFolderRepository() {
        super(Folder::id, Comparator.comparing(Folder::createdAt).thenComparing(Folder::id));
    }
}
