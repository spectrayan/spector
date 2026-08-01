import os

src_dir = r"D:\git\spector\memory\spector-memory\src"

def process_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()
        
    original = list(lines)
    
    # Check if this file is in com.spectrayan.spector.memory exactly (no subpackages)
    # or com.spectrayan.spector.memory.kernel.shape
    is_in_memory_pkg = False
    is_in_shape_pkg = False
    for line in lines:
        if line.startswith("package com.spectrayan.spector.memory;"):
            is_in_memory_pkg = True
            break
        if line.startswith("package com.spectrayan.spector.memory.kernel.shape;"):
            is_in_shape_pkg = True
            break
            
    # Fix RegistryLayout
    # Old import -> new import
    for i in range(len(lines)):
        lines[i] = lines[i].replace("import com.spectrayan.spector.memory.kernel.shape.RegistryLayout;", "import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;")
        lines[i] = lines[i].replace("import com.spectrayan.spector.memory.StorageLayout;", "import com.spectrayan.spector.memory.kernel.StorageLayout;")
        
    # If in memory pkg, it might use StorageLayout without import
    if is_in_memory_pkg and not file_path.endswith("StorageLayout.java"):
        # Check if StorageLayout is used
        uses_storage = any("StorageLayout" in line for line in lines)
        if uses_storage:
            # Need to add import
            # Find the last import, or the line after package
            insert_idx = -1
            for i, line in enumerate(lines):
                if line.startswith("import "):
                    insert_idx = i
            
            if insert_idx == -1:
                # No imports, find package
                for i, line in enumerate(lines):
                    if line.startswith("package "):
                        insert_idx = i + 1
                        break
            
            if insert_idx != -1:
                lines.insert(insert_idx, "import com.spectrayan.spector.memory.kernel.StorageLayout;\n")
                
    # If in shape pkg, it might use RegistryLayout without import
    if is_in_shape_pkg and not file_path.endswith("RegistryLayout.java"):
        uses_registry = any("RegistryLayout" in line for line in lines)
        if uses_registry:
            insert_idx = -1
            for i, line in enumerate(lines):
                if line.startswith("import "):
                    insert_idx = i
            if insert_idx == -1:
                for i, line in enumerate(lines):
                    if line.startswith("package "):
                        insert_idx = i + 1
                        break
            if insert_idx != -1:
                lines.insert(insert_idx, "import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;\n")
                
    if lines != original:
        with open(file_path, "w", encoding="utf-8") as f:
            f.writelines(lines)
        print("Updated", file_path)

for root, _, files in os.walk(src_dir):
    for f in files:
        if f.endswith(".java"):
            process_file(os.path.join(root, f))
