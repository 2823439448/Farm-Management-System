// 注册处理函数
// 在成功时返回 true，失败时返回 false
function handleRegister(event) {
    // 阻止表单默认提交行为，如果 event 存在
    if (event) {
        event.preventDefault();
    }

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirm-password').value;

    // 1. 检查所有字段是否填写
    if (!username || !password || !confirmPassword) {
        alert('请填写所有字段（用户名、密码、确认密码）！');
        return false; // 返回失败状态
    }

    // 2. 检查密码是否一致
    if (password !== confirmPassword) {
        alert('错误：两次输入的密码不一致！');
        // 清空密码字段以提示用户重新输入
        document.getElementById('password').value = '';
        document.getElementById('confirm-password').value = '';
        return false; // 返回失败状态
    }

    // 3. 执行注册逻辑 (此处仅为模拟)
    // 实际项目中，这里应包含 AJAX 请求将数据发送到后端进行用户创建

    // 模拟注册成功
    alert(`恭喜您，注册成功！\n用户名: ${username}`);

    // 清空表单
    document.getElementById('register-form').reset();

    return true; // 返回成功状态
}

// “登录”按钮处理函数：现在会先调用注册逻辑，成功后才执行跳转
function handleLoginRedirect(event) {
    // 1. 调用注册逻辑
    // 注意：我们将 event 传递给 handleRegister，以便它可以调用 event.preventDefault()
    const isRegisterSuccessful = handleRegister(event);

    // 2. 如果注册成功，则执行跳转
    if (isRegisterSuccessful) {
        //alert('注册成功，正在跳转到登录页面...');
        // 实际跳转到指定的登录页面
        window.location.href = '/login/login.html';
    } else {
        // 如果注册失败（例如密码不一致或字段未填写），handleRegister 中已经有提示
        console.log('注册失败，无法跳转到登录页。');
    }
}


// 添加事件监听器
document.addEventListener('DOMContentLoaded', function() {
    // 绑定“完成”按钮（注册）：继续只调用 handleRegister
    document.getElementById('register-button').addEventListener('click', handleRegister);

    // 绑定“登录”按钮（跳转）：现在调用 handleLoginRedirect
    document.getElementById('login-button').addEventListener('click', handleLoginRedirect);

    // 绑定回车键事件到表单，使其默认触发注册（完成按钮的逻辑）
    document.getElementById('register-form').addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            event.preventDefault(); // 阻止表单默认提交行为
            // 回车默认只执行注册，不执行跳转
            handleRegister(event);
        }
    });
});