package com.crysound.opscenter.execution;

import com.crysound.opscenter.integration.ParameterDefinition;
import org.springframework.stereotype.Component;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/** 根据登记参数校验和规范化，禁止浏览器扩展成任意 JSON 调用。 */
@Component
public class TaskParameterValidator {
    private static final Set<String> TYPES = Set.of("text", "date", "datetime", "integer", "boolean", "enum", "text-list");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /** 即便没有填值，未知字段类型也不可执行，避免错误降级。 */
    public Map<String,Object> validate(List<ParameterDefinition> definitions, Map<String,Object> supplied) {
        if (definitions == null || definitions.size() > 100 || supplied == null || supplied.size() > 100) throw invalid();
        Set<String> names = new HashSet<>();
        for (ParameterDefinition p : definitions) {
            if (p == null || p.name() == null || !names.add(p.name()) || p.type() == null || !TYPES.contains(p.type())
                    || (p.maxLength() != null && p.maxLength() < 1)
                    || (p.min() != null && p.max() != null && p.min() > p.max())
                    || ("enum".equals(p.type()) && (p.options() == null || p.options().isEmpty())))
                throw new ExecutionException(409, "UNSUPPORTED_PARAMETERS", "该动作的参数定义暂不支持执行");
        }
        if (!names.containsAll(supplied.keySet())) throw invalid();
        Map<String,Object> normalized = new TreeMap<>();
        for (ParameterDefinition p : definitions) {
            Object value = supplied.get(p.name());
            if (value == null || value instanceof String s && s.isBlank() || value instanceof List<?> list && list.isEmpty()) {
                if (p.required()) throw invalid(p,"REQUIRED","必填，请填写此项");
                continue;
            }
            int max = p.maxLength() == null ? 4096 : Math.min(p.maxLength(), 4096);
            Object result;
            try {
                result = switch (p.type()) {
                    case "text" -> text(p,value,max);
                    case "enum" -> {
                        String s = text(p,value,max);
                        if (!p.options().contains(s)) throw invalid(p,"INVALID_OPTION","请选择登记的选项");
                        yield s;
                    }
                    case "date" -> LocalDate.parse(text(p,value,Math.min(max, 10)), DATE).format(DATE);
                    case "datetime" -> {
                        String s = text(p,value,max).replace('T',' ');
                        if (s.length() == 16) s += ":00";
                        yield LocalDateTime.parse(s,DATETIME).format(DATETIME);
                    }
                    case "integer" -> {
                        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                                || value instanceof Long || value instanceof BigInteger)) throw invalid(p,"INVALID_TYPE","请填写整数");
                        long n = value instanceof BigInteger b ? b.longValueExact() : ((Number)value).longValue();
                        if (p.min() != null && n < p.min() || p.max() != null && n > p.max()) throw invalid(p,"OUT_OF_RANGE",integerRule(p));
                        yield n;
                    }
                    case "boolean" -> { if (!(value instanceof Boolean)) throw invalid(p,"INVALID_TYPE","请选择是或否"); yield value; }
                    case "text-list" -> {
                        // text-list 的 maxLength 是项目数；每条文本另设固定安全上限。
                        int itemLimit = p.maxLength() == null ? 100 : Math.min(p.maxLength(),100);
                        if (!(value instanceof List<?> list)) throw invalid(p,"INVALID_TYPE","请逐行填写文本列表");
                        if (list.size() > itemLimit) throw invalid(p,"TOO_MANY_ITEMS","最多 " + itemLimit + " 项");
                        List<String> items = new ArrayList<>();
                        for (Object entry : list) {
                            String s = text(p,entry,4096);
                            if (s.isBlank()) throw invalid(p,"REQUIRED_ITEM","列表项目不能为空");
                            items.add(s);
                        }
                        yield List.copyOf(items);
                    }
                    default -> throw invalid();
                };
            } catch (DateTimeParseException ex) {
                throw invalid(p,"INVALID_DATE","date".equals(p.type()) ? "请填写有效日期，格式 yyyy-MM-dd" : "请填写有效日期时间，格式 yyyy-MM-dd HH:mm:ss");
            } catch (ArithmeticException ex) { throw invalid(p,"OUT_OF_RANGE",integerRule(p)); }
            normalized.put(p.name(),result);
        }
        return Collections.unmodifiableMap(normalized);
    }
    private String text(ParameterDefinition p,Object value,int max) {
        if (!(value instanceof String s)) throw invalid(p,"INVALID_TYPE","请填写文本");
        if (s.length() > max) throw invalid(p,"TOO_LONG","最多 " + max + " 个字符");
        if (s.indexOf('\0') >= 0) throw invalid(p,"INVALID_TEXT","文本不能包含空字符");
        return s;
    }
    private String integerRule(ParameterDefinition p) {
        if (p.min() != null && p.max() != null) return "请填写 " + p.min() + "～" + p.max() + " 之间的整数";
        if (p.min() != null) return "请填写不小于 " + p.min() + " 的整数";
        if (p.max() != null) return "请填写不大于 " + p.max() + " 的整数";
        return "请填写 64 位有符号整数";
    }
    private ExecutionException invalid(ParameterDefinition p,String code,String rule) {
        String label = p.label() == null || p.label().isBlank() ? p.name() : p.label();
        String message = label + "：" + rule;
        return new ExecutionException(400,"INVALID_PARAMETERS",message,
                List.of(new ExecutionException.FieldError(p.name(),label,code,rule,message)));
    }
    private ExecutionException invalid() { return new ExecutionException(400,"INVALID_PARAMETERS","参数类型、必填项或范围不符合登记规则"); }
}
