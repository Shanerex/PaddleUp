package com.paddleup.packs.model;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.service.DataAdapter;
import java.nio.file.Path;
import java.util.Optional;

/** A pack that passed loading, with its file and the data adapter its key resolved to. */
public record LoadedPack(Path file, PackDto pack, Optional<DataAdapter> adapter) {}
