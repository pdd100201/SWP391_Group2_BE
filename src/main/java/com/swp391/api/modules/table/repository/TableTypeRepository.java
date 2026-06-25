package com.swp391.api.modules.table.repository;

import com.swp391.api.modules.table.entity.TableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository để thao tác với bảng {@code table_types} trong cơ sở dữ liệu.
 *
 * <p>Cung cấp phương thức tìm loại bàn theo tên (dùng khi tạo/cập nhật bàn
 * từ client gửi lên chuỗi type name thay vì ID).</p>
 */
public interface TableTypeRepository extends JpaRepository<TableType, Long> {

    /**
     * Tìm loại bàn theo tên, không phân biệt chữ hoa/thường.
     * Dùng trong {@code TableServiceImpl} khi client gửi tên loại bàn
     * (ví dụ: "Main Hall") thay vì gửi {@code table_type_id}.
     *
     * @param typeName Tên loại bàn cần tìm
     * @return Optional chứa TableType nếu tìm thấy
     */
    Optional<TableType> findByTypeNameIgnoreCase(String typeName);

    /**
     * Kiểm tra tên loại bàn đã tồn tại chưa.
     *
     * @param typeName Tên loại bàn cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByTypeNameIgnoreCase(String typeName);
}
